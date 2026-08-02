package io.aegisops.worker.outbox;

import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import io.aegisops.worker.job.JobResult;
import io.aegisops.worker.job.OutboxJob;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Worker-side dispatcher that picks pending rows from {@code automation_outbox}, routes them to the
 * matching {@link OutboxJob} by {@code job_name}, and records the outcome.
 *
 * <p>Scheduling is intentionally driven by the {@code WorkerRuntimeTopology} (P6) — this class
 * stays free of {@code @Scheduled} annotations so it can be unit-tested with {@link #tick()} called
 * directly.
 */
@Service
public class OutboxPoller {

  private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPoller.class);

  private final OutboxRepository outboxRepository;
  private final OutboxProperties outboxProperties;
  private final Map<String, OutboxJob> jobsByName;

  public OutboxPoller(
      OutboxRepository outboxRepository, OutboxProperties outboxProperties, List<OutboxJob> jobs) {
    this.outboxRepository = outboxRepository;
    this.outboxProperties = outboxProperties;
    Map<String, OutboxJob> map = new HashMap<>();
    for (OutboxJob job : jobs) {
      OutboxJob previous = map.put(job.jobName(), job);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate OutboxJob for jobName=" + job.jobName() + ": " + previous + " and " + job);
      }
    }
    this.jobsByName = Map.copyOf(map);
  }

  /** Visible for testing. */
  public Map<String, OutboxJob> jobsByName() {
    return jobsByName;
  }

  /**
   * Run one poll tick. Returns the number of rows successfully completed.
   *
   * <p>Unmatched {@code job_name} rows are treated as failures with reason {@code "UNKNOWN_JOB"} so
   * they quickly drain through retries rather than block the queue.
   */
  public int tick() {
    if (!outboxProperties.enabled()) {
      return 0;
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    outboxRepository.recoverExpiredLeases(outboxProperties.targetApp(), now);
    String claimToken = UUID.randomUUID().toString();
    List<AutomationOutboxRecord> claimed =
        outboxRepository.claimNextPending(
            outboxProperties.targetApp(),
            outboxProperties.batchSize(),
            now.plusNanos(outboxProperties.leaseDurationMs() * 1_000_000L),
            claimToken);
    if (claimed.isEmpty()) {
      return 0;
    }

    int success = 0;
    LeaseHeartbeat heartbeat = startLeaseHeartbeat(claimed);
    try {
      for (AutomationOutboxRecord row : claimed) {
        if (!renewClaimBeforeHandle(row)) {
          LOGGER.warn("Skipping outbox row {} after claim ownership was lost", row.getId());
          continue;
        }
        OutboxJob job = jobsByName.get(row.getJobName());
        JobResult result;
        try {
          if (job == null) {
            result = JobResult.failure("UNKNOWN_JOB:" + row.getJobName());
          } else {
            result = job.handle(row);
          }
        } catch (RuntimeException ex) {
          LOGGER.warn("Outbox job {} threw for row {}", row.getJobName(), row.getId(), ex);
          result = JobResult.failure(ex.getClass().getSimpleName() + ":" + ex.getMessage());
        }

        if (result.isSuccess()) {
          if (outboxRepository.markDone(row.getId(), row.getClaimToken(), OffsetDateTime.now())) {
            success++;
          }
        } else {
          outboxRepository.recordFailure(row.getId(), row.getClaimToken(), result.reason());
        }
      }
    } finally {
      heartbeat.close();
    }
    return success;
  }

  private boolean renewClaimBeforeHandle(AutomationOutboxRecord row) {
    OffsetDateTime leaseUntil =
        OffsetDateTime.now(ZoneOffset.UTC)
            .plusNanos(outboxProperties.leaseDurationMs() * 1_000_000L);
    try {
      return outboxRepository.extendLease(
          row.getId(), outboxProperties.targetApp(), row.getClaimToken(), leaseUntil);
    } catch (RuntimeException ex) {
      LOGGER.warn("Failed to confirm outbox claim for row {}", row.getId(), ex);
      return false;
    }
  }

  private LeaseHeartbeat startLeaseHeartbeat(List<AutomationOutboxRecord> rows) {
    AtomicBoolean running = new AtomicBoolean(true);
    long intervalMs = Math.max(250L, outboxProperties.leaseDurationMs() / 3L);
    Thread thread =
        Thread.startVirtualThread(
            () -> {
              while (running.get()) {
                try {
                  Thread.sleep(intervalMs);
                } catch (InterruptedException ex) {
                  Thread.currentThread().interrupt();
                  return;
                }
                if (!running.get()) {
                  return;
                }
                OffsetDateTime leaseUntil =
                    OffsetDateTime.now(ZoneOffset.UTC)
                        .plusNanos(outboxProperties.leaseDurationMs() * 1_000_000L);
                for (AutomationOutboxRecord row : rows) {
                  try {
                    boolean extended =
                        outboxRepository.extendLease(
                            row.getId(),
                            outboxProperties.targetApp(),
                            row.getClaimToken(),
                            leaseUntil);
                    OutboxJob job = jobsByName.get(row.getJobName());
                    if (extended && job != null && !job.renewLease(row, leaseUntil)) {
                      LOGGER.warn("Failed to extend job lease for outbox row {}", row.getId());
                    }
                  } catch (RuntimeException ex) {
                    LOGGER.warn("Failed to extend outbox lease for row {}", row.getId(), ex);
                  }
                }
              }
            });
    return new LeaseHeartbeat(running, thread);
  }

  private record LeaseHeartbeat(AtomicBoolean running, Thread thread) implements AutoCloseable {
    @Override
    public void close() {
      running.set(false);
      thread.interrupt();
      try {
        thread.join(1000L);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }
  }
}

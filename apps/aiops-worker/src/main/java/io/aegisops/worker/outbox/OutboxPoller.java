package io.aegisops.worker.outbox;

import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import io.aegisops.worker.job.JobResult;
import io.aegisops.worker.job.OutboxJob;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
  @Transactional
  public int tick() {
    if (!outboxProperties.enabled()) {
      return 0;
    }
    List<AutomationOutboxRecord> claimed =
        outboxRepository.claimNextPending(
            outboxProperties.targetApp(), outboxProperties.batchSize());
    if (claimed.isEmpty()) {
      return 0;
    }

    int success = 0;
    for (AutomationOutboxRecord row : claimed) {
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
        outboxRepository.markDone(row.getId(), OffsetDateTime.now());
        success++;
      } else {
        outboxRepository.recordFailure(row.getId(), result.reason());
      }
    }
    return success;
  }
}

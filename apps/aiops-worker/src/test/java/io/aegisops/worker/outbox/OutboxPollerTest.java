package io.aegisops.worker.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.persistence.jooq.tables.records.AutomationOutboxRecord;
import io.aegisops.worker.job.JobResult;
import io.aegisops.worker.job.OutboxJob;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutboxPollerTest {

  @Test
  void routesRowsToRegisteredJobByJobName() {
    NoopOutboxRepository repository = new NoopOutboxRepository();
    repository.pending.add(row("row-1", "zabbix-sync"));
    repository.pending.add(row("row-2", "incident-aggregate"));

    CountingJob zabbix = new CountingJob("zabbix-sync");
    CountingJob incident = new CountingJob("incident-aggregate");

    OutboxPoller poller =
        new OutboxPoller(
            repository, new OutboxProperties(true, 1000L, 10, "worker"), List.of(zabbix, incident));

    int done = poller.tick();

    assertEquals(2, done);
    assertEquals(1, zabbix.calls);
    assertEquals(1, incident.calls);
    assertEquals("done", repository.statuses.get("row-1"));
    assertEquals("done", repository.statuses.get("row-2"));
  }

  @Test
  void routesUnknownJobToFailure() {
    NoopOutboxRepository repository = new NoopOutboxRepository();
    repository.pending.add(row("row-1", "unknown-job"));

    OutboxPoller poller =
        new OutboxPoller(
            repository,
            new OutboxProperties(true, 1000L, 10, "worker"),
            List.of(new CountingJob("zabbix-sync")));

    int done = poller.tick();

    assertEquals(0, done);
    assertEquals("pending", repository.statuses.get("row-1"));
    assertTrue(repository.errorMessages.get("row-1").startsWith("UNKNOWN_JOB"));
  }

  @Test
  void propagatesJobExceptionAsFailure() {
    NoopOutboxRepository repository = new NoopOutboxRepository();
    repository.pending.add(row("row-1", "explode"));

    OutboxPoller poller =
        new OutboxPoller(
            repository,
            new OutboxProperties(true, 1000L, 10, "worker"),
            List.of(
                new OutboxJob() {
                  @Override
                  public String jobName() {
                    return "explode";
                  }

                  @Override
                  public JobResult handle(AutomationOutboxRecord row) {
                    throw new IllegalStateException("boom");
                  }
                }));

    int done = poller.tick();

    assertEquals(0, done);
    assertEquals("pending", repository.statuses.get("row-1"));
    assertTrue(repository.errorMessages.get("row-1").contains("boom"));
  }

  @Test
  void emptyClaimReturnsZero() {
    NoopOutboxRepository repository = new NoopOutboxRepository();

    OutboxPoller poller =
        new OutboxPoller(
            repository,
            new OutboxProperties(true, 1000L, 10, "worker"),
            List.of(new CountingJob("zabbix-sync")));

    assertEquals(0, poller.tick());
  }

  @Test
  void disabledPollerSkipsTick() {
    NoopOutboxRepository repository = new NoopOutboxRepository();
    repository.pending.add(row("row-1", "zabbix-sync"));

    OutboxPoller poller =
        new OutboxPoller(
            repository,
            new OutboxProperties(false, 1000L, 10, "worker"),
            List.of(new CountingJob("zabbix-sync")));

    assertEquals(0, poller.tick());
  }

  @Test
  void duplicateJobRegistrationFailsFast() {
    NoopOutboxRepository repository = new NoopOutboxRepository();
    try {
      new OutboxPoller(
          repository,
          new OutboxProperties(true, 1000L, 10, "worker"),
          List.of(new CountingJob("dup"), new CountingJob("dup")));
      org.junit.jupiter.api.Assertions.fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("dup"));
    }
  }

  private static AutomationOutboxRecord row(String id, String jobName) {
    return new AutomationOutboxRecord()
        .setId(id)
        .setTargetApp("worker")
        .setJobName(jobName)
        .setStatus("pending")
        .setRetryCount(0)
        .setMaxRetries(3);
  }

  private static final class CountingJob implements OutboxJob {
    private final String name;
    int calls;

    private CountingJob(String name) {
      this.name = name;
    }

    @Override
    public String jobName() {
      return name;
    }

    @Override
    public JobResult handle(AutomationOutboxRecord row) {
      calls++;
      return JobResult.success();
    }
  }

  @SuppressWarnings("unused")
  private static List<AutomationOutboxRecord> empty() {
    return new ArrayList<>();
  }
}

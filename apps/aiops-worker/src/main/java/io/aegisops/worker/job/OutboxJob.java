package io.aegisops.worker.job;

import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import java.time.OffsetDateTime;

/**
 * Common contract for any outbox-driven job executed by the worker process.
 *
 * <p>Each implementation picks the rows it can serve by {@link #jobName()} and uses {@link
 * io.aegisops.worker.outbox.OutboxRepository#findById} to fetch the payload.
 *
 * <p>Implementations must be idempotent: the {@code OutboxPoller} may retry the same row up to
 * {@code max_retries} times after a failure, and the side effects of {@link #handle} on external
 * systems must tolerate that.
 */
public interface OutboxJob {

  /** Stable identifier matched against {@code automation_outbox.job_name}. */
  String jobName();

  /**
   * Execute the job for the given outbox row.
   *
   * @return {@link JobResult#success()} if the side effect landed; {@link JobResult#failure} with a
   *     short reason otherwise
   */
  JobResult handle(AutomationOutboxRecord row);

  /** Renew any job-specific lease that shares ownership with the outbox row. */
  default boolean renewLease(AutomationOutboxRecord row, OffsetDateTime leaseUntil) {
    return true;
  }
}

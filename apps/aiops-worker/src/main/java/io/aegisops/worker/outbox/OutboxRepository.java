package io.aegisops.worker.outbox;

import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JOOQ-backed repository for the {@code automation_outbox} table.
 *
 * <p>All three apps (server, worker, runner) share this table; each app reads only the rows whose
 * {@code target_app} matches its {@link OutboxProperties#targetApp()}. Worker-only writes from the
 * rest of this app (see {@code OutboxPoller}) call {@link #markDone}, {@link #markFailed}, and
 * {@link #incrementRetry}.
 *
 * <p>Cross-app writes (server → outbox, runner → outbox) go through the {@code server} or {@code
 * runner} repository side. Each app owns the rows it picks up.
 */
public interface OutboxRepository {

  /**
   * Lock and return up to {@code batchSize} pending rows for {@code targetApp}.
   *
   * <p>Implementation MUST set {@code status='processing'} in the same transaction so concurrent
   * pollers do not double-pick.
   */
  List<AutomationOutboxRecord> claimNextPending(String targetApp, int batchSize);

  /** Read a row by id. */
  Optional<AutomationOutboxRecord> findById(String id);

  /** Mark {@code id} as completed at {@code processedAt}. */
  boolean markDone(String id, OffsetDateTime processedAt);

  /**
   * Mark {@code id} as failed with {@code errorMessage}.
   *
   * @return {@code true} if the row was updated and {@code retry_count} has not yet exceeded the
   *     per-row {@code max_retries}; otherwise the row stays pending for the next sweep.
   */
  boolean recordFailure(String id, String errorMessage);

  /**
   * Reset a stuck row from {@code processing} back to {@code pending} when its lease expired.
   *
   * <p>Currently unused (MVP uses max_retries only), but reserved for the future sweep that detects
   * a worker crash mid-job.
   */
  boolean resetProcessing(String id);
}

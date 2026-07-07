package io.aegisops.worker.outbox;

import static io.aegisops.persistence.jooq.public_.Tables.AUTOMATION_OUTBOX;

import io.aegisops.persistence.jooq.tables.records.AutomationOutboxRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * Default JOOQ-backed implementation of {@link OutboxRepository}.
 *
 * <p>All write operations take the same JOOQ {@code DSLContext} (transactional Spring bean) so the
 * row lock + status update happen atomically. {@link #claimNextPending} is the only method that
 * does not need a pre-existing row id and therefore opens its own transaction implicitly via the
 * {@code UPDATE ... RETURNING} pattern.
 */
@Repository
public class JooqOutboxRepository implements OutboxRepository {

  private final DSLContext dsl;

  public JooqOutboxRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public List<AutomationOutboxRecord> claimNextPending(String targetApp, int batchSize) {
    return dsl.update(AUTOMATION_OUTBOX)
        .set(AUTOMATION_OUTBOX.STATUS, "processing")
        .set(AUTOMATION_OUTBOX.UPDATED_AT, OffsetDateTime.now())
        .where(
            AUTOMATION_OUTBOX.STATUS.eq("pending").and(AUTOMATION_OUTBOX.TARGET_APP.eq(targetApp)))
        .orderBy(AUTOMATION_OUTBOX.CREATED_AT.asc())
        .limit(batchSize)
        .returning()
        .fetch();
  }

  @Override
  public Optional<AutomationOutboxRecord> findById(String id) {
    return Optional.ofNullable(
        dsl.selectFrom(AUTOMATION_OUTBOX).where(AUTOMATION_OUTBOX.ID.eq(id)).fetchOne());
  }

  @Override
  public boolean markDone(String id, OffsetDateTime processedAt) {
    int updated =
        dsl.update(AUTOMATION_OUTBOX)
            .set(AUTOMATION_OUTBOX.STATUS, "done")
            .set(AUTOMATION_OUTBOX.PROCESSED_AT, processedAt)
            .set(AUTOMATION_OUTBOX.ERROR_MESSAGE, (String) null)
            .set(AUTOMATION_OUTBOX.UPDATED_AT, OffsetDateTime.now())
            .where(AUTOMATION_OUTBOX.ID.eq(id))
            .execute();
    return updated == 1;
  }

  @Override
  public boolean recordFailure(String id, String errorMessage) {
    int nextRetry =
        Optional.ofNullable(
                    dsl.select(AUTOMATION_OUTBOX.RETRY_COUNT)
                        .from(AUTOMATION_OUTBOX)
                        .where(AUTOMATION_OUTBOX.ID.eq(id))
                        .fetchOne(AUTOMATION_OUTBOX.RETRY_COUNT))
                .orElse(0)
            + 1;

    int maxRetries =
        Optional.ofNullable(
                dsl.select(AUTOMATION_OUTBOX.MAX_RETRIES)
                    .from(AUTOMATION_OUTBOX)
                    .where(AUTOMATION_OUTBOX.ID.eq(id))
                    .fetchOne(AUTOMATION_OUTBOX.MAX_RETRIES))
            .orElse(3);

    String newStatus = nextRetry >= maxRetries ? "failed" : "pending";

    int updated =
        dsl.update(AUTOMATION_OUTBOX)
            .set(AUTOMATION_OUTBOX.RETRY_COUNT, nextRetry)
            .set(AUTOMATION_OUTBOX.STATUS, newStatus)
            .set(AUTOMATION_OUTBOX.ERROR_MESSAGE, errorMessage)
            .set(AUTOMATION_OUTBOX.UPDATED_AT, OffsetDateTime.now())
            .where(AUTOMATION_OUTBOX.ID.eq(id))
            .execute();
    return updated == 1;
  }

  @Override
  public boolean resetProcessing(String id) {
    int updated =
        dsl.update(AUTOMATION_OUTBOX)
            .set(AUTOMATION_OUTBOX.STATUS, "pending")
            .set(AUTOMATION_OUTBOX.UPDATED_AT, OffsetDateTime.now())
            .where(AUTOMATION_OUTBOX.ID.eq(id).and(AUTOMATION_OUTBOX.STATUS.eq("processing")))
            .execute();
    return updated == 1;
  }
}

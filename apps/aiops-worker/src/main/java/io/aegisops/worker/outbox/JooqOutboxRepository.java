package io.aegisops.worker.outbox;

import static io.aegisops.persistence.jooq.public_.Tables.AUTOMATION_OUTBOX;

import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
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
  public List<AutomationOutboxRecord> claimNextPending(
      String targetApp, int batchSize, OffsetDateTime leaseUntil) {
    return dsl.fetch(
            """
                with candidates as (
                    select id
                      from automation_outbox
                     where target_app = ?
                       and status = 'pending'
                       and available_at <= now()
                     order by available_at, created_at
                     for update skip locked
                     limit ?
                )
                update automation_outbox outbox
                   set status = 'processing', lease_until = ?, updated_at = now()
                  from candidates
                 where outbox.id = candidates.id
                returning outbox.*
                """,
            targetApp,
            batchSize,
            leaseUntil)
        .into(AutomationOutboxRecord.class);
  }

  @Override
  public int recoverExpiredLeases(String targetApp, OffsetDateTime now) {
    return dsl.execute(
        """
            update automation_outbox
               set status = 'pending', lease_until = null, updated_at = now()
             where target_app = ? and status = 'processing' and lease_until < ?
            """,
        targetApp,
        now);
  }

  @Override
  public boolean extendLease(String id, String targetApp, OffsetDateTime leaseUntil) {
    return dsl.execute(
            """
            update automation_outbox
               set lease_until = ?, updated_at = now()
             where id = ? and target_app = ? and status = 'processing'
            """,
            leaseUntil,
            id,
            targetApp)
        == 1;
  }

  @Override
  public Optional<AutomationOutboxRecord> findById(String id) {
    return Optional.ofNullable(
        dsl.selectFrom(AUTOMATION_OUTBOX).where(AUTOMATION_OUTBOX.ID.eq(id)).fetchOne());
  }

  @Override
  public boolean markDone(String id, OffsetDateTime processedAt) {
    int updated =
        dsl.execute(
            """
                update automation_outbox
                   set status = 'done', processed_at = ?, lease_until = null,
                       error_message = null, updated_at = now()
                 where id = ? and status = 'processing'
                """,
            processedAt,
            id);
    return updated == 1;
  }

  @Override
  public boolean recordFailure(String id, String errorMessage) {
    int updated =
        dsl.execute(
            """
                update automation_outbox
                   set retry_count = retry_count + 1,
                       status = case when retry_count + 1 >= max_retries
                                     then 'failed' else 'pending' end,
                       available_at = case when retry_count + 1 >= max_retries then available_at
                                           else now() + make_interval(
                                             secs => least(300, power(2, retry_count)::integer)) end,
                       lease_until = null,
                       error_message = ?,
                       updated_at = now()
                 where id = ? and status = 'processing'
                """,
            errorMessage,
            id);
    return updated == 1;
  }

  @Override
  public boolean resetProcessing(String id) {
    int updated =
        dsl.execute(
            """
                update automation_outbox
                   set status = 'pending', lease_until = null, updated_at = now()
                 where id = ? and status = 'processing'
                """,
            id);
    return updated == 1;
  }
}

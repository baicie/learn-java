package io.aegisops.worker.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.outbox.OutboxMessage;
import io.aegisops.common.outbox.OutboxWriter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class OutboxReplayPostgresTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("outbox_replay_test")
          .withUsername("aiops")
          .withPassword("aiops");

  private JdbcTemplate jdbc;
  private OutboxWriter writer;
  private JooqOutboxRepository repository;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource datasource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    datasource.setDriverClassName("org.postgresql.Driver");
    jdbc = new JdbcTemplate(datasource);
    jdbc.execute("drop table if exists automation_outbox");
    jdbc.execute(
        """
        create table automation_outbox (
          id varchar(64) primary key,
          tenant_id varchar(64),
          target_app varchar(32) not null,
          job_name varchar(64) not null,
          payload jsonb not null,
          status varchar(32) not null,
          retry_count integer not null,
          max_retries integer not null,
          error_message text,
          available_at timestamptz not null,
          lease_until timestamptz,
          claim_token varchar(64),
          idempotency_key varchar(128),
          replay_requested boolean not null default false,
          created_at timestamptz not null,
          updated_at timestamptz not null,
          processed_at timestamptz
        )
        """);
    jdbc.execute(
        """
        create unique index uq_automation_outbox_idempotency
          on automation_outbox(target_app, job_name, idempotency_key)
         where idempotency_key is not null
        """);
    writer = new OutboxWriter(jdbc, new ObjectMapper());
    repository =
        new JooqOutboxRepository(
            DSL.using(datasource, SQLDialect.POSTGRES, new Settings().withRenderSchema(false)));
  }

  @Test
  void producerBeforeFinalFailureKeepsReplayPending() {
    OutboxMessage message = message("tenant-a", "same-key");
    String id = writer.enqueue(message);
    markFinalAttemptProcessing(id, "claim-a");

    assertThat(writer.enqueueOrRequeueFailed(message)).isEqualTo(id);
    assertProcessingOwnership(id, "claim-a");
    assertThat(repository.recordFailure(id, "claim-a", "aggregation failed")).isTrue();

    assertReplayPending(id);
  }

  @Test
  void finalFailureBeforeProducerKeepsReplayPending() {
    OutboxMessage message = message("tenant-a", "same-key");
    String id = writer.enqueue(message);
    markFinalAttemptProcessing(id, "claim-a");

    assertThat(repository.recordFailure(id, "claim-a", "aggregation failed")).isTrue();
    assertThat(writer.enqueueOrRequeueFailed(message)).isEqualTo(id);

    assertReplayPending(id);
  }

  @Test
  void successfulProcessingConsumesReplayIntent() {
    OutboxMessage message = message("tenant-a", "same-key");
    String id = writer.enqueue(message);
    markFinalAttemptProcessing(id, "claim-a");

    writer.enqueueOrRequeueFailed(message);
    assertThat(repository.markDone(id, "claim-a", OffsetDateTime.now(ZoneOffset.UTC))).isTrue();

    Map<String, Object> state =
        jdbc.queryForMap("select status, replay_requested from automation_outbox where id = ?", id);
    assertThat(state).containsEntry("status", "done").containsEntry("replay_requested", false);
  }

  @Test
  void expiredProcessingLeaseConsumesReplayIntent() {
    OutboxMessage message = message("tenant-a", "same-key");
    String id = writer.enqueue(message);
    markFinalAttemptProcessing(id, "claim-a");
    writer.enqueueOrRequeueFailed(message);
    jdbc.update(
        "update automation_outbox set lease_until = now() - interval '1 second' where id = ?", id);

    assertThat(repository.recoverExpiredLeases("worker", OffsetDateTime.now(ZoneOffset.UTC)))
        .isEqualTo(1);

    assertReplayPending(id);
  }

  @Test
  void tenantCollisionCannotRewriteExistingRow() {
    OutboxMessage tenantA = message("tenant-a", "same-key");
    OutboxMessage tenantB = message("tenant-b", "same-key");
    String id = writer.enqueue(tenantA);
    jdbc.update(
        "update automation_outbox set status = 'failed', retry_count = max_retries where id = ?",
        id);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> writer.enqueueOrRequeueFailed(tenantB))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tenant");

    Map<String, Object> state =
        jdbc.queryForMap(
            "select tenant_id, status, payload ->> 'tenant' as payload_tenant from automation_outbox where id = ?",
            id);
    assertThat(state)
        .containsEntry("tenant_id", "tenant-a")
        .containsEntry("status", "failed")
        .containsEntry("payload_tenant", "tenant-a");
  }

  @Test
  void ordinaryEnqueueTenantCollisionFailsClosed() {
    OutboxMessage tenantA = message("tenant-a", "ordinary-key");
    OutboxMessage tenantB = message("tenant-b", "ordinary-key");
    String id = writer.enqueue(tenantA);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> writer.enqueue(tenantB))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tenant");

    assertThat(
            jdbc.queryForObject(
                "select tenant_id from automation_outbox where id = ?", String.class, id))
        .isEqualTo("tenant-a");
  }

  private OutboxMessage message(String tenantId, String idempotencyKey) {
    return new OutboxMessage(
        tenantId,
        "worker",
        "incident-aggregation",
        Map.of("tenant", tenantId),
        idempotencyKey,
        3,
        null);
  }

  private void markFinalAttemptProcessing(String id, String claimToken) {
    jdbc.update(
        """
        update automation_outbox
           set status = 'processing', retry_count = max_retries - 1,
               claim_token = ?, lease_until = now() + interval '5 minutes'
         where id = ?
        """,
        claimToken,
        id);
  }

  private void assertProcessingOwnership(String id, String claimToken) {
    Map<String, Object> state =
        jdbc.queryForMap(
            "select status, claim_token, lease_until > now() as leased from automation_outbox where id = ?",
            id);
    assertThat(state)
        .containsEntry("status", "processing")
        .containsEntry("claim_token", claimToken)
        .containsEntry("leased", true);
  }

  private void assertReplayPending(String id) {
    Map<String, Object> state =
        jdbc.queryForMap(
            "select status, retry_count, replay_requested, claim_token from automation_outbox where id = ?",
            id);
    assertThat(state)
        .containsEntry("status", "pending")
        .containsEntry("retry_count", 0)
        .containsEntry("replay_requested", false)
        .containsEntry("claim_token", null);
  }
}

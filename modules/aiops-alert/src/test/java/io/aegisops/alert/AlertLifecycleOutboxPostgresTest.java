package io.aegisops.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.outbox.OutboxMessage;
import io.aegisops.common.outbox.OutboxWriter;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class AlertLifecycleOutboxPostgresTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("alert_lifecycle_test")
          .withUsername("aiops")
          .withPassword("aiops");

  private JdbcTemplate jdbc;
  private AlertIngestService service;
  private OutboxWriter outboxWriter;
  private TransactionTemplate transactions;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource datasource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    datasource.setDriverClassName("org.postgresql.Driver");
    jdbc = new JdbcTemplate(datasource);
    transactions = new TransactionTemplate(new DataSourceTransactionManager(datasource));

    jdbc.execute("drop table if exists automation_outbox");
    jdbc.execute("drop table if exists alert_event");
    jdbc.execute(
        """
        create table alert_event (
          id varchar(64) primary key,
          tenant_id varchar(64) not null,
          source varchar(64) not null,
          source_event_id varchar(256),
          severity varchar(32) not null,
          title varchar(512) not null,
          description text,
          asset_id varchar(64),
          entity_type varchar(64),
          entity_name varchar(256),
          labels jsonb not null,
          starts_at timestamptz not null,
          ends_at timestamptz,
          status varchar(32) not null,
          raw_payload jsonb not null,
          fingerprint varchar(512) not null,
          aggregation_key varchar(512),
          updated_at timestamptz not null,
          created_at timestamptz not null
        )
        """);
    jdbc.execute(
        """
        create unique index uq_alert_event_tenant_source_source_event_id
          on alert_event(tenant_id, source, source_event_id)
         where source_event_id is not null
        """);
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

    ObjectMapper objectMapper = new ObjectMapper();
    outboxWriter = new OutboxWriter(jdbc, objectMapper);
    service =
        new AlertIngestService(
            new AlertFingerprintPolicy(),
            new AlertIngestRepository(jdbc),
            objectMapper,
            outboxWriter);
  }

  @Test
  void repeatedAlertIngestRequeuesFailedLifecycleOutboxRow() {
    AlertIngestRequest request = request();
    AlertIngestResult first = service.ingest("tenant-1", request);
    String idempotencyKey = "alert-lifecycle:" + first.alertId() + ":open";
    String outboxId = outboxId(idempotencyKey);
    jdbc.update(
        """
        update automation_outbox
           set status = 'failed', retry_count = max_retries,
               error_message = 'incident aggregation failed',
               available_at = now() + interval '1 day'
         where id = ?
        """,
        outboxId);

    AlertIngestResult repeated = service.ingest("tenant-1", request);

    assertThat(repeated.created()).isFalse();
    assertThat(outboxId(idempotencyKey)).isEqualTo(outboxId);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from automation_outbox where idempotency_key = ?",
                Integer.class,
                idempotencyKey))
        .isEqualTo(1);
    Map<String, Object> state =
        jdbc.queryForMap(
            """
            select status, retry_count, error_message,
                   payload ->> 'created' as payload_created,
                   available_at <= now() as available_now
              from automation_outbox
             where id = ?
            """,
            outboxId);
    assertThat(state)
        .containsEntry("status", "pending")
        .containsEntry("retry_count", 0)
        .containsEntry("error_message", null)
        .containsEntry("payload_created", "false")
        .containsEntry("available_now", true);
  }

  @Test
  void repeatedAlertIngestLeavesCompletedLifecycleOutboxTerminal() {
    AlertIngestRequest request = request();
    AlertIngestResult first = service.ingest("tenant-1", request);
    String idempotencyKey = "alert-lifecycle:" + first.alertId() + ":open";
    String outboxId = outboxId(idempotencyKey);
    jdbc.update(
        """
        update automation_outbox
           set status = 'done', processed_at = now()
         where id = ?
        """,
        outboxId);

    service.ingest("tenant-1", request);

    Map<String, Object> state =
        jdbc.queryForMap(
            """
            select status, payload ->> 'created' as payload_created,
                   processed_at is not null as processed
              from automation_outbox
             where id = ?
            """,
            outboxId);
    assertThat(state)
        .containsEntry("status", "done")
        .containsEntry("payload_created", "true")
        .containsEntry("processed", true);
  }

  @Test
  void ordinaryEnqueueLeavesFailedOutboxRowTerminal() {
    OutboxMessage message =
        new OutboxMessage(
            "tenant-1",
            "worker",
            "unrelated-job",
            Map.of("attempt", 1),
            "unrelated-job:key-1",
            3,
            null);
    String outboxId = outboxWriter.enqueue(message);
    jdbc.update(
        """
        update automation_outbox
           set status = 'failed', retry_count = max_retries,
               error_message = 'unrelated job failed'
         where id = ?
        """,
        outboxId);

    assertThat(outboxWriter.enqueue(message)).isEqualTo(outboxId);

    assertThat(
            jdbc.queryForObject(
                "select status from automation_outbox where id = ?", String.class, outboxId))
        .isEqualTo("failed");
  }

  @Test
  void surroundingTransactionRollsBackAlertAndLifecycleOutboxTogether() {
    transactions.executeWithoutResult(
        status -> {
          service.ingest("tenant-1", request());
          status.setRollbackOnly();
        });

    assertThat(jdbc.queryForObject("select count(*) from alert_event", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("select count(*) from automation_outbox", Integer.class))
        .isZero();
  }

  private String outboxId(String idempotencyKey) {
    return jdbc.queryForObject(
        "select id from automation_outbox where idempotency_key = ?", String.class, idempotencyKey);
  }

  private AlertIngestRequest request() {
    return new AlertIngestRequest(
        "zabbix",
        "datasource-1:event-1",
        "high",
        "CPU saturation",
        null,
        "asset-1",
        "host",
        "demo-host",
        Map.of("env", "demo"),
        null,
        null,
        "open",
        Map.of("eventid", "event-1"));
  }
}

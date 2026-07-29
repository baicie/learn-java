package io.aegisops.datasource.infrastructure.persistence;

import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.outbox.OutboxWriter;
import io.aegisops.datasource.DataSourceService;
import io.aegisops.datasource.application.ManualDataSourceSyncApplicationService;
import io.aegisops.kubernetes.application.KubernetesInventoryClientFactory;
import io.aegisops.zabbix.ZabbixClientFactory;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

abstract class DataSourceSyncStorePostgresTestSupport {
  protected static final String TENANT_ID = "tenant-a";
  protected static final String DATASOURCE_ID = "ds-a";

  protected JdbcTemplate jdbc;
  protected JdbcDataSourceSyncStore store;
  protected TransactionTemplate transactions;
  protected DriverManagerDataSource datasource;

  protected abstract PostgreSQLContainer<?> postgres();

  @BeforeEach
  void setUpDatasourceSyncSchema() {
    PostgreSQLContainer<?> postgres = postgres();
    datasource =
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    datasource.setDriverClassName("org.postgresql.Driver");
    jdbc = new JdbcTemplate(datasource);
    transactions = new TransactionTemplate(new DataSourceTransactionManager(datasource));
    jdbc.execute("drop table if exists automation_outbox");
    jdbc.execute("drop table if exists datasource_sync_write_marker");
    jdbc.execute("drop table if exists datasource_sync_run");
    jdbc.execute("drop table if exists datasource");
    jdbc.execute("drop table if exists tenant");
    jdbc.execute(
        """
        create table tenant (
          id varchar(64) primary key,
          status varchar(32) not null
        )
        """);
    jdbc.execute(
        """
        create table datasource (
          id varchar(64) primary key,
          tenant_id varchar(64) not null,
          type varchar(32) not null,
          name varchar(160) not null default 'Zabbix',
          status varchar(32) not null,
          config_json jsonb not null,
          last_sync_at timestamptz,
          created_at timestamptz not null default now(),
          updated_at timestamptz not null default now()
        )
        """);
    jdbc.execute(
        """
        create table datasource_sync_run (
          id varchar(64) primary key,
          tenant_id varchar(64) not null,
          datasource_id varchar(64) not null,
          sync_type varchar(32),
          status varchar(32) not null,
          message text,
          stats_json jsonb,
          started_at timestamptz not null,
          finished_at timestamptz,
          claim_token varchar(64),
          lease_until timestamptz,
          datasource_updated_at timestamptz,
          created_by varchar(64)
        )
        """);
    createOutboxSchema();
    jdbc.execute(
        """
        create table datasource_sync_write_marker (
          id bigserial primary key,
          claim_token varchar(64) not null
        )
        """);
    jdbc.update("insert into tenant(id, status) values (?, 'active')", TENANT_ID);
    jdbc.update(
        """
        insert into datasource(id, tenant_id, type, status, config_json)
        values (?, ?, 'zabbix', 'active', '{"endpoint":"https://zabbix.example"}'::jsonb)
        """,
        DATASOURCE_ID,
        TENANT_ID);
    store = new JdbcDataSourceSyncStore(jdbc, new ObjectMapper());
  }

  private void createOutboxSchema() {
    jdbc.execute(
        """
        create table automation_outbox (
          id varchar(64) primary key,
          tenant_id varchar(64) not null,
          target_app varchar(64) not null,
          job_name varchar(128) not null,
          status varchar(32) not null,
          payload jsonb not null,
          retry_count int not null default 0,
          max_retries int not null default 3,
          error_message text,
          available_at timestamptz not null default now(),
          lease_until timestamptz,
          claim_token varchar(64),
          idempotency_key varchar(128),
          replay_requested boolean not null default false,
          created_at timestamptz not null default now(),
          updated_at timestamptz not null default now(),
          processed_at timestamptz
        )
        """);
    jdbc.execute(
        """
        create unique index uq_automation_outbox_idempotency
        on automation_outbox(target_app, job_name, idempotency_key)
        where idempotency_key is not null
        """);
  }

  protected void insertRun(String runId, String status, OffsetDateTime startedAt) {
    jdbc.update(
        "insert into datasource_sync_run(id, tenant_id, datasource_id, status, started_at) values (?, ?, ?, ?, ?)",
        runId,
        TENANT_ID,
        DATASOURCE_ID,
        status,
        startedAt);
  }

  protected DataSourceService dataSourceService() {
    JdbcDataSourceSyncStore syncStore = new JdbcDataSourceSyncStore(jdbc, new ObjectMapper());
    return new DataSourceService(
        jdbc,
        new ObjectMapper(),
        mock(ZabbixClientFactory.class),
        mock(KubernetesInventoryClientFactory.class),
        new ManualDataSourceSyncApplicationService(
            mock(OutboxWriter.class), syncStore, new JdbcZabbixSyncDispatchStore(jdbc)));
  }

  protected void insertExpiredRunningRun(String runId, OffsetDateTime now) {
    insertRun(runId, "running", now.minusMinutes(10));
    jdbc.update(
        "update datasource_sync_run set claim_token = ?, lease_until = ? where id = ?",
        "claim-expired",
        now.minusSeconds(1),
        runId);
  }

  protected void insertProcessingOutbox(String runId, OffsetDateTime leaseUntil) {
    jdbc.update(
        """
        insert into automation_outbox(
          id, tenant_id, target_app, job_name, status, payload, lease_until
        ) values (?, ?, 'worker', 'zabbix-sync', 'processing', ?::jsonb, ?)
        """,
        "outbox-" + runId,
        TENANT_ID,
        "{\"runId\":\"" + runId + "\",\"datasourceId\":\"" + DATASOURCE_ID + "\"}",
        leaseUntil);
  }

  protected String statusOf(String runId) {
    return jdbc.queryForObject(
        "select status from datasource_sync_run where id = ?", String.class, runId);
  }

  protected String messageOf(String runId) {
    return jdbc.queryForObject(
        "select message from datasource_sync_run where id = ?", String.class, runId);
  }

  protected String datasourceStatus() {
    return jdbc.queryForObject(
        "select status from datasource where id = ?", String.class, DATASOURCE_ID);
  }

  protected OffsetDateTime datasourceUpdatedAt() {
    return jdbc.queryForObject(
        "select updated_at from datasource where id = ?", OffsetDateTime.class, DATASOURCE_ID);
  }

  protected OffsetDateTime datasourceVersionOfRun(String runId) {
    return jdbc.queryForObject(
        "select datasource_updated_at from datasource_sync_run where id = ?",
        OffsetDateTime.class,
        runId);
  }
}

package io.aegisops.datasource.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.common.outbox.OutboxWriter;
import io.aegisops.datasource.DataSourceService;
import io.aegisops.datasource.application.ManualDataSourceSyncApplicationService;
import io.aegisops.datasource.application.ZabbixSyncDispatchApplicationService;
import io.aegisops.kubernetes.application.KubernetesInventoryClientFactory;
import io.aegisops.zabbix.ZabbixClientFactory;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcZabbixSyncDispatchRecoveryPostgresTest extends DataSourceSyncStorePostgresTestSupport {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("zabbix_dispatch_recovery_test")
          .withUsername("aiops")
          .withPassword("aiops");

  @Override
  protected PostgreSQLContainer<?> postgres() {
    return POSTGRES;
  }

  @Test
  void manualSyncReplacesPendingRunAfterItsDispatchExhausts() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-stuck", "pending", now.minusMinutes(5));
    insertDispatch("outbox-stuck", "failed", TENANT_ID, DATASOURCE_ID, "sync-stuck");

    var response = new AtomicReference<io.aegisops.datasource.api.dto.StartSyncResponse>();
    assertThatCode(
            () ->
                response.set(
                    transactions.execute(
                        status ->
                            dataSourceService(realOutboxWriter())
                                .startSync(TENANT_ID, DATASOURCE_ID))))
        .doesNotThrowAnyException();

    assertThat(response.get()).isNotNull();
    assertThat(statusOf("sync-stuck")).isEqualTo("failed");
    assertThat(messageOf("sync-stuck")).isEqualTo("Sync dispatch failed before execution");
    assertThat(finishedAt("sync-stuck")).isNotNull();
    assertThat(statusOf(response.get().runId())).isEqualTo("pending");
    assertThat(pendingRunCount()).isEqualTo(1);
    assertThat(pendingOutboxCount()).isEqualTo(1);
    assertThat(outboxStatus("outbox-stuck")).isEqualTo("failed");
    assertThat(datasourceStatus()).isEqualTo("active");
  }

  @Test
  void scheduledSyncReplacesPendingRunAfterItsDispatchExhausts() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-stuck", "pending", now.minusMinutes(5));
    insertDispatch("outbox-stuck", "failed", TENANT_ID, DATASOURCE_ID, "sync-stuck");

    int dispatched = dispatch(now);

    assertThat(dispatched).isEqualTo(1);
    assertThat(statusOf("sync-stuck")).isEqualTo("failed");
    assertThat(pendingRunCount()).isEqualTo(1);
    assertThat(pendingOutboxCount()).isEqualTo(1);
    assertThat(outboxStatus("outbox-stuck")).isEqualTo("failed");
  }

  @Test
  void pendingDispatchRemainsFailClosedRegardlessOfAge() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-pending", "pending", now.minusYears(1));
    insertDispatch("outbox-pending", "pending", TENANT_ID, DATASOURCE_ID, "sync-pending");

    assertThat(dispatch(now)).isZero();
    assertManualSyncBlocked();
    assertThat(statusOf("sync-pending")).isEqualTo("pending");
  }

  @Test
  void processingDispatchRemainsFailClosedRegardlessOfAge() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-processing", "pending", now.minusYears(1));
    insertDispatch("outbox-processing", "processing", TENANT_ID, DATASOURCE_ID, "sync-processing");

    assertThat(dispatch(now)).isZero();
    assertManualSyncBlocked();
    assertThat(statusOf("sync-processing")).isEqualTo("pending");
  }

  @Test
  void terminalFailureRequiresTheExactTenantDatasourceAndRunTriple() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-pending", "pending", now.minusMinutes(5));
    insertDispatch("wrong-tenant", "failed", "tenant-other", DATASOURCE_ID, "sync-pending");
    insertDispatch("wrong-datasource", "failed", TENANT_ID, "ds-other", "sync-pending");
    insertDispatch("wrong-run", "failed", TENANT_ID, DATASOURCE_ID, "sync-other");

    assertThat(dispatch(now)).isZero();
    assertManualSyncBlocked();
    assertThat(statusOf("sync-pending")).isEqualTo("pending");
  }

  @Test
  void terminalDispatchDoesNotOverwriteRunningOrSuccessfulRuns() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-running", "running", now.minusMinutes(5));
    jdbc.update(
        "update datasource_sync_run set claim_token=?, lease_until=? where id=?",
        "claim-a",
        now.plusMinutes(5),
        "sync-running");
    insertDispatch("outbox-running", "failed", TENANT_ID, DATASOURCE_ID, "sync-running");
    JdbcZabbixSyncDispatchStore dispatchStore = new JdbcZabbixSyncDispatchStore(jdbc);

    assertThat(dispatchStore.failPendingRunsWithTerminalDispatchFailure(TENANT_ID, DATASOURCE_ID))
        .isZero();
    assertThat(dispatch(now)).isZero();
    assertThat(statusOf("sync-running")).isEqualTo("running");

    jdbc.update(
        "update datasource_sync_run set status='success', finished_at=now(), lease_until=null where id=?",
        "sync-running");
    assertThat(dispatchStore.failPendingRunsWithTerminalDispatchFailure(TENANT_ID, DATASOURCE_ID))
        .isZero();
    assertThat(dispatch(now)).isEqualTo(1);
    assertThat(statusOf("sync-running")).isEqualTo("success");
  }

  @Test
  void concurrentScheduledTicksCreateOneReplacementRunAndOutbox() throws Exception {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-stuck", "pending", now.minusMinutes(5));
    insertDispatch("outbox-stuck", "failed", TENANT_ID, DATASOURCE_ID, "sync-stuck");
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<Integer> first = executor.submit(() -> dispatchWhenStarted(now, ready, start));
      Future<Integer> second = executor.submit(() -> dispatchWhenStarted(now, ready, start));
      ready.await();
      start.countDown();

      assertThat(first.get() + second.get()).isEqualTo(1);
    }
    assertThat(statusOf("sync-stuck")).isEqualTo("failed");
    assertThat(pendingRunCount()).isEqualTo(1);
    assertThat(pendingOutboxCount()).isEqualTo(1);
    assertThat(totalOutboxCount()).isEqualTo(2);
  }

  private int dispatchWhenStarted(OffsetDateTime now, CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    ready.countDown();
    start.await();
    return dispatch(now);
  }

  private int dispatch(OffsetDateTime now) {
    JdbcZabbixSyncDispatchStore dispatchStore = new JdbcZabbixSyncDispatchStore(jdbc);
    ZabbixSyncDispatchApplicationService service =
        new ZabbixSyncDispatchApplicationService(dispatchStore, realOutboxWriter());
    Integer dispatched =
        transactions.execute(status -> service.dispatchDue(now, Duration.ofMinutes(1), 10));
    return dispatched == null ? 0 : dispatched;
  }

  private void assertManualSyncBlocked() {
    assertThatThrownBy(
            () ->
                transactions.execute(
                    status ->
                        dataSourceService(realOutboxWriter()).startSync(TENANT_ID, DATASOURCE_ID)))
        .isInstanceOfSatisfying(
            AppException.class,
            exception ->
                assertThat(exception.errorCode()).isEqualTo("DATASOURCE_SYNC_ALREADY_RUNNING"));
  }

  private DataSourceService dataSourceService(OutboxWriter outboxWriter) {
    JdbcDataSourceSyncStore syncStore = new JdbcDataSourceSyncStore(jdbc, new ObjectMapper());
    return new DataSourceService(
        jdbc,
        new ObjectMapper(),
        org.mockito.Mockito.mock(ZabbixClientFactory.class),
        org.mockito.Mockito.mock(KubernetesInventoryClientFactory.class),
        new ManualDataSourceSyncApplicationService(
            outboxWriter, syncStore, new JdbcZabbixSyncDispatchStore(jdbc)));
  }

  private OutboxWriter realOutboxWriter() {
    return new OutboxWriter(jdbc, new ObjectMapper());
  }

  private void insertDispatch(
      String id, String status, String tenantId, String datasourceId, String runId) {
    jdbc.update(
        """
        insert into automation_outbox(
          id, tenant_id, target_app, job_name, status, payload, error_message
        ) values (?, ?, 'worker', 'zabbix-sync', ?, jsonb_build_object(
          'tenantId', ?, 'datasourceId', ?, 'runId', ?), 'UNKNOWN_JOB:zabbix-sync')
        """,
        id,
        TENANT_ID,
        status,
        tenantId,
        datasourceId,
        runId);
  }

  private int pendingRunCount() {
    return count("select count(*) from datasource_sync_run where status='pending'");
  }

  private int pendingOutboxCount() {
    return count("select count(*) from automation_outbox where status='pending'");
  }

  private int totalOutboxCount() {
    return count("select count(*) from automation_outbox");
  }

  private int count(String sql) {
    Integer count = jdbc.queryForObject(sql, Integer.class);
    return count == null ? 0 : count;
  }

  private OffsetDateTime finishedAt(String runId) {
    return jdbc.queryForObject(
        "select finished_at from datasource_sync_run where id=?", OffsetDateTime.class, runId);
  }

  private String outboxStatus(String id) {
    return jdbc.queryForObject("select status from automation_outbox where id=?", String.class, id);
  }
}

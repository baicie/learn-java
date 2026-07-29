package io.aegisops.datasource.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.alert.AlertIngestResult;
import io.aegisops.alert.AlertIngestService;
import io.aegisops.alert.application.AlertAssetReconciliationApplicationService;
import io.aegisops.asset.api.dto.AssetUpsertResult;
import io.aegisops.asset.application.AssetApplicationService;
import io.aegisops.common.exception.AppException;
import io.aegisops.datasource.application.DataSourceSyncApplicationService;
import io.aegisops.datasource.application.DataSourceSyncWriteFence;
import io.aegisops.datasource.application.DataSourceSyncWriteService;
import io.aegisops.datasource.application.port.DataSourceSyncStore.SyncRunClaimResult;
import io.aegisops.datasource.zabbix.ZabbixSyncMapper;
import io.aegisops.incident.application.IncidentAssetReconciliationApplicationService;
import io.aegisops.zabbix.ZabbixClient;
import io.aegisops.zabbix.ZabbixClientFactory;
import io.aegisops.zabbix.ZabbixConfig;
import io.aegisops.zabbix.ZabbixHost;
import io.aegisops.zabbix.ZabbixProblem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class DataSourceSyncWriteFencePostgresTest extends DataSourceSyncStorePostgresTestSupport {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("datasource_sync_write_fence_test")
          .withUsername("aiops")
          .withPassword("aiops");

  @Override
  protected PostgreSQLContainer<?> postgres() {
    return POSTGRES;
  }

  @Test
  void staleWorkerCannotWriteAfterAnotherWorkerTakesOverItsClaim() throws Exception {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-pending", "pending", now.minusMinutes(1));
    insertProcessingOutbox("sync-pending", now.plusMinutes(5));

    CountDownLatch lastStandaloneRenewalCommitted = new CountDownLatch(1);
    CountDownLatch allowStaleWorkerToContinue = new CountDownLatch(1);
    JdbcDataSourceSyncStore observedStore =
        storePausedAfterRenewal(3, lastStandaloneRenewalCommitted, allowStaleWorkerToContinue);

    ZabbixClientFactory clientFactory = mock(ZabbixClientFactory.class);
    ZabbixClient client = mock(ZabbixClient.class);
    when(clientFactory.create(any(ZabbixConfig.class))).thenReturn(client);
    when(client.getHosts(1000))
        .thenReturn(
            List.of(
                new ZabbixHost(
                    "10084",
                    "demo-host",
                    "Demo host",
                    "0",
                    "10.0.0.84",
                    List.of("demo"),
                    "machine-10084",
                    new ObjectMapper().createObjectNode())));
    when(client.getProblems(1000)).thenReturn(List.of());

    AssetApplicationService assetService = mock(AssetApplicationService.class);
    when(assetService.upsert(any()))
        .thenAnswer(
            invocation -> {
              jdbc.update(
                  "insert into datasource_sync_write_marker(claim_token) values (?)", "claim-a");
              return new AssetUpsertResult("asset-a", "source-link-a", "created", false);
            });
    DataSourceSyncApplicationService applicationService =
        syncApplicationService(
            observedStore, clientFactory, assetService, mock(AlertIngestService.class));

    assertStaleWorkerIsFenced(
        applicationService,
        observedStore,
        lastStandaloneRenewalCommitted,
        allowStaleWorkerToContinue);
  }

  @Test
  void staleWorkerCannotIngestAlertAfterAnotherWorkerTakesOverItsClaim() throws Exception {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-pending", "pending", now.minusMinutes(1));
    insertProcessingOutbox("sync-pending", now.plusMinutes(5));

    CountDownLatch lastStandaloneRenewalCommitted = new CountDownLatch(1);
    CountDownLatch allowStaleWorkerToContinue = new CountDownLatch(1);
    JdbcDataSourceSyncStore observedStore =
        storePausedAfterRenewal(6, lastStandaloneRenewalCommitted, allowStaleWorkerToContinue);

    ZabbixClientFactory clientFactory = mock(ZabbixClientFactory.class);
    ZabbixClient client = mock(ZabbixClient.class);
    when(clientFactory.create(any(ZabbixConfig.class))).thenReturn(client);
    when(client.getHosts(1000)).thenReturn(List.of());
    when(client.getProblems(1000))
        .thenReturn(
            List.of(
                new ZabbixProblem(
                    "20001",
                    "30001",
                    "CPU high",
                    4,
                    Instant.parse("2026-07-28T10:00:00Z"),
                    List.of("10084"),
                    Map.of("env", "demo", "service", "order-service"),
                    new ObjectMapper().createObjectNode())));

    AssetApplicationService assetService = mock(AssetApplicationService.class);
    when(assetService.markMissing(eq(TENANT_ID), eq("zabbix"), eq(DATASOURCE_ID), any()))
        .thenReturn(0);
    AlertIngestService alertService = mock(AlertIngestService.class);
    when(alertService.ingest(eq(TENANT_ID), any(), anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              jdbc.update(
                  "insert into datasource_sync_write_marker(claim_token) values (?)", "claim-a");
              return new AlertIngestResult("alert-a", true, "fingerprint-a", "aggregation-a");
            });
    DataSourceSyncApplicationService applicationService =
        syncApplicationService(observedStore, clientFactory, assetService, alertService);

    assertStaleWorkerIsFenced(
        applicationService,
        observedStore,
        lastStandaloneRenewalCommitted,
        allowStaleWorkerToContinue);
  }

  @Test
  void datasourceUpdateCannotCommitBeforeClaimedWrite() throws Exception {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-pending", "pending", now.minusMinutes(1));
    assertThat(store.claimForExecution(TENANT_ID, DATASOURCE_ID, "sync-pending", "claim-a"))
        .isEqualTo(SyncRunClaimResult.ACQUIRED);

    DataSourceSyncWriteFence writeFence =
        new DataSourceSyncWriteFence(store, new DataSourceTransactionManager(datasource));
    CountDownLatch claimedWriteStarted = new CountDownLatch(1);
    CountDownLatch allowClaimedWrite = new CountDownLatch(1);
    CountDownLatch datasourceUpdateStarted = new CountDownLatch(1);
    AtomicInteger datasourceUpdaterPid = new AtomicInteger();

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<?> claimedWrite =
          executor.submit(
              () ->
                  writeFence.executeClaimedWrite(
                      TENANT_ID,
                      DATASOURCE_ID,
                      "sync-pending",
                      "claim-a",
                      () -> {
                        claimedWriteStarted.countDown();
                        await(allowClaimedWrite);
                        insertWriteMarker("claimed-write");
                        return null;
                      }));
      assertThat(claimedWriteStarted.await(10, TimeUnit.SECONDS)).isTrue();

      Future<Void> datasourceUpdate =
          executor.submit(
              () -> updateDatasourceConfiguration(datasourceUpdateStarted, datasourceUpdaterPid));
      assertThat(datasourceUpdateStarted.await(10, TimeUnit.SECONDS)).isTrue();
      awaitDatasourceUpdaterAtDatabase(datasourceUpdaterPid.get(), datasourceUpdate);
      allowClaimedWrite.countDown();

      claimedWrite.get(10, TimeUnit.SECONDS);
      datasourceUpdate.get(10, TimeUnit.SECONDS);
    } finally {
      allowClaimedWrite.countDown();
    }

    assertThat(
            jdbc.queryForList(
                "select claim_token from datasource_sync_write_marker order by id", String.class))
        .containsExactly("claimed-write", "datasource-update");
  }

  private DataSourceSyncApplicationService syncApplicationService(
      JdbcDataSourceSyncStore observedStore,
      ZabbixClientFactory clientFactory,
      AssetApplicationService assetService,
      AlertIngestService alertService) {
    DataSourceSyncWriteService writeService =
        new DataSourceSyncWriteService(
            new DataSourceSyncWriteFence(
                observedStore, new DataSourceTransactionManager(datasource)),
            assetService,
            alertService,
            mock(AlertAssetReconciliationApplicationService.class),
            mock(IncidentAssetReconciliationApplicationService.class));
    return new DataSourceSyncApplicationService(
        observedStore, clientFactory, new ZabbixSyncMapper(), writeService, new ObjectMapper());
  }

  private void assertStaleWorkerIsFenced(
      DataSourceSyncApplicationService applicationService,
      JdbcDataSourceSyncStore observedStore,
      CountDownLatch lastStandaloneRenewalCommitted,
      CountDownLatch allowStaleWorkerToContinue)
      throws Exception {
    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      Future<?> staleWorker =
          executor.submit(
              () ->
                  applicationService.execute(TENANT_ID, DATASOURCE_ID, "sync-pending", "claim-a"));
      assertThat(lastStandaloneRenewalCommitted.await(10, TimeUnit.SECONDS)).isTrue();

      jdbc.update(
          "update datasource_sync_run set lease_until = now() - interval '1 second' where id = ?",
          "sync-pending");
      assertThat(
              observedStore.claimForExecution(TENANT_ID, DATASOURCE_ID, "sync-pending", "claim-b"))
          .isEqualTo(SyncRunClaimResult.ACQUIRED);
      allowStaleWorkerToContinue.countDown();

      assertThatThrownBy(() -> staleWorker.get(10, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(AppException.class);
    } finally {
      allowStaleWorkerToContinue.countDown();
    }

    assertThat(
            jdbc.queryForObject("select count(*) from datasource_sync_write_marker", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select claim_token from datasource_sync_run where id = ?",
                String.class,
                "sync-pending"))
        .isEqualTo("claim-b");
  }

  private JdbcDataSourceSyncStore storePausedAfterRenewal(
      int renewalToPause, CountDownLatch renewalCommitted, CountDownLatch allowWorkerToContinue) {
    JdbcDataSourceSyncStore observedStore = spy(store);
    AtomicInteger renewalCount = new AtomicInteger();
    doAnswer(
            invocation -> {
              boolean renewed = (Boolean) invocation.callRealMethod();
              if (renewed && renewalCount.incrementAndGet() == renewalToPause) {
                renewalCommitted.countDown();
                if (!allowWorkerToContinue.await(10, TimeUnit.SECONDS)) {
                  throw new IllegalStateException("timed out waiting to release stale worker");
                }
              }
              return renewed;
            })
        .when(observedStore)
        .renewClaim(eq(TENANT_ID), eq(DATASOURCE_ID), eq("sync-pending"), eq("claim-a"), any());
    return observedStore;
  }

  private Void updateDatasourceConfiguration(CountDownLatch updateStarted, AtomicInteger updaterPid)
      throws SQLException {
    try (Connection connection = datasource.getConnection()) {
      connection.setAutoCommit(false);
      updaterPid.set(backendPid(connection));
      updateStarted.countDown();
      try (PreparedStatement update =
          connection.prepareStatement(
              """
              update datasource
              set config_json = '{"endpoint":"https://new-zabbix.example"}'::jsonb,
                  status = 'inactive',
                  updated_at = updated_at + interval '1 second'
              where tenant_id = ? and id = ?
              """)) {
        update.setString(1, TENANT_ID);
        update.setString(2, DATASOURCE_ID);
        assertThat(update.executeUpdate()).isEqualTo(1);
      }
      try (PreparedStatement marker =
          connection.prepareStatement(
              "insert into datasource_sync_write_marker(claim_token) values (?)")) {
        marker.setString(1, "datasource-update");
        marker.executeUpdate();
      }
      connection.commit();
      return null;
    }
  }

  private int backendPid(Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("select pg_backend_pid()");
        ResultSet result = statement.executeQuery()) {
      assertThat(result.next()).isTrue();
      return result.getInt(1);
    }
  }

  private void awaitDatasourceUpdaterAtDatabase(int updaterPid, Future<?> datasourceUpdate)
      throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!datasourceUpdate.isDone() && !isWaitingForLock(updaterPid)) {
      if (System.nanoTime() >= deadline) {
        throw new IllegalStateException("timed out waiting for datasource update");
      }
      TimeUnit.MILLISECONDS.sleep(10);
    }
  }

  private boolean isWaitingForLock(int backendPid) {
    Boolean waiting =
        jdbc.queryForObject(
            """
            select coalesce(
              (select wait_event_type = 'Lock' from pg_stat_activity where pid = ?),
              false
            )
            """,
            Boolean.class,
            backendPid);
    return Boolean.TRUE.equals(waiting);
  }

  private void insertWriteMarker(String marker) {
    jdbc.update("insert into datasource_sync_write_marker(claim_token) values (?)", marker);
  }

  private void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("timed out waiting to continue claimed write");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "interrupted while waiting to continue claimed write", exception);
    }
  }
}

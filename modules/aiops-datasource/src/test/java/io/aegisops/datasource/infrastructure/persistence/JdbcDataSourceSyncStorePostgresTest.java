package io.aegisops.datasource.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.datasource.DataSourceService;
import io.aegisops.datasource.application.port.DataSourceSyncStore.SyncRunClaimResult;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcDataSourceSyncStorePostgresTest extends DataSourceSyncStorePostgresTestSupport {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("datasource_sync_test")
          .withUsername("aiops")
          .withPassword("aiops");

  @Override
  protected PostgreSQLContainer<?> postgres() {
    return POSTGRES;
  }

  @Test
  void scheduledDispatchReplacesAnExpiredRunningRun() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertExpiredRunningRun("sync-expired", now);
    JdbcZabbixSyncDispatchStore dispatchStore = new JdbcZabbixSyncDispatchStore(jdbc);

    assertThat(dispatchStore.lockDueTargets(now, 10))
        .containsExactly(
            new io.aegisops.datasource.application.port.ZabbixSyncDispatchStore.SyncTarget(
                TENANT_ID, DATASOURCE_ID));
    assertThat(dispatchStore.createScheduledRun("sync-scheduled", TENANT_ID, DATASOURCE_ID, now))
        .isTrue();

    assertThat(statusOf("sync-expired")).isEqualTo("failed");
    assertThat(messageOf("sync-expired")).isEqualTo("Sync lease expired");
    assertThat(statusOf("sync-scheduled")).isEqualTo("pending");
  }

  @Test
  void manualSyncReplacesAnExpiredRunningRun() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertExpiredRunningRun("sync-expired", now);
    DataSourceService service = dataSourceService();

    var response = service.startSync(TENANT_ID, DATASOURCE_ID);

    assertThat(statusOf("sync-expired")).isEqualTo("failed");
    assertThat(messageOf("sync-expired")).isEqualTo("Sync lease expired");
    assertThat(statusOf(response.runId())).isEqualTo("pending");
  }

  @Test
  void kubernetesLegacyRunWithoutLeaseDoesNotBlockANewManualSync() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    jdbc.update(
        "update datasource set type='kubernetes', config_json=?::jsonb where tenant_id=? and id=?",
        "{\"endpoint\":\"https://kubernetes.example\",\"apiToken\":\"test-token\"}",
        TENANT_ID,
        DATASOURCE_ID);
    insertRun("sync-kubernetes-crashed", "running", now.minusMinutes(10));

    var response = dataSourceService().startSync(TENANT_ID, DATASOURCE_ID);

    assertThat(statusOf("sync-kubernetes-crashed")).isEqualTo("running");
    assertThat(statusOf(response.runId())).isEqualTo("pending");
  }

  @Test
  void pendingRunTakesPrecedenceOverAnOlderFailedRetryForTheSameDatasource() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-failed", "failed", now.minusMinutes(2));
    insertRun("sync-pending", "pending", now.minusMinutes(1));
    insertProcessingOutbox("sync-failed", now.plusMinutes(5));
    insertProcessingOutbox("sync-pending", now.plusMinutes(5));

    assertThat(store.claimForExecution(TENANT_ID, DATASOURCE_ID, "sync-failed", "claim-a"))
        .isEqualTo(SyncRunClaimResult.ACTIVE);
    assertThat(store.claimForExecution(TENANT_ID, DATASOURCE_ID, "sync-pending", "claim-b"))
        .isEqualTo(SyncRunClaimResult.ACQUIRED);

    assertThat(statusOf("sync-failed")).isEqualTo("failed");
    assertThat(statusOf("sync-pending")).isEqualTo("running");
  }

  @Test
  void concurrentRetriesForOneDatasourceGrantOnlyOneClaim() throws Exception {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-failed-a", "failed", now.minusMinutes(2));
    insertRun("sync-failed-b", "failed", now.minusMinutes(1));
    insertProcessingOutbox("sync-failed-a", now.plusMinutes(5));
    insertProcessingOutbox("sync-failed-b", now.plusMinutes(5));

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<SyncRunClaimResult> first =
          executor.submit(() -> claimWhenStarted("sync-failed-a", "claim-a", ready, start));
      Future<SyncRunClaimResult> second =
          executor.submit(() -> claimWhenStarted("sync-failed-b", "claim-b", ready, start));
      ready.await();
      start.countDown();

      assertThat(first.get()).isIn(SyncRunClaimResult.ACQUIRED, SyncRunClaimResult.ACTIVE);
      assertThat(second.get()).isIn(SyncRunClaimResult.ACQUIRED, SyncRunClaimResult.ACTIVE);
      assertThat(
              java.util.stream.Stream.of(first.get(), second.get())
                  .filter(SyncRunClaimResult.ACQUIRED::equals)
                  .count())
          .isEqualTo(1);
    }
  }

  @Test
  void inactiveTenantAbandonsQueuedSyncSoSchedulingCanRestart() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-pending", "pending", now.minusMinutes(1));
    insertProcessingOutbox("sync-pending", now.plusMinutes(5));
    jdbc.update("update tenant set status='inactive' where id=?", TENANT_ID);

    assertThat(store.claimForExecution(TENANT_ID, DATASOURCE_ID, "sync-pending", "claim-a"))
        .isEqualTo(SyncRunClaimResult.NOT_FOUND);
    assertThat(statusOf("sync-pending")).isEqualTo("failed");
    assertThat(messageOf("sync-pending")).isEqualTo("Sync target is no longer active");

    jdbc.update("update tenant set status='active' where id=?", TENANT_ID);
    JdbcZabbixSyncDispatchStore dispatchStore = new JdbcZabbixSyncDispatchStore(jdbc);
    assertThat(dispatchStore.lockDueTargets(now, 10)).hasSize(1);
    assertThat(dispatchStore.createScheduledRun("sync-restarted", TENANT_ID, DATASOURCE_ID, now))
        .isTrue();
    assertThat(statusOf("sync-restarted")).isEqualTo("pending");
  }

  @Test
  void inactiveDatasourceAbandonsQueuedSyncSoManualSyncCanRestart() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-pending", "pending", now.minusMinutes(1));
    insertProcessingOutbox("sync-pending", now.plusMinutes(5));
    jdbc.update(
        "update datasource set status='inactive' where tenant_id=? and id=?",
        TENANT_ID,
        DATASOURCE_ID);

    assertThat(store.claimForExecution(TENANT_ID, DATASOURCE_ID, "sync-pending", "claim-a"))
        .isEqualTo(SyncRunClaimResult.NOT_FOUND);
    assertThat(statusOf("sync-pending")).isEqualTo("failed");
    assertThat(messageOf("sync-pending")).isEqualTo("Sync target is no longer active");

    jdbc.update(
        "update datasource set status='active' where tenant_id=? and id=?",
        TENANT_ID,
        DATASOURCE_ID);
    var response = dataSourceService().startSync(TENANT_ID, DATASOURCE_ID);
    assertThat(statusOf(response.runId())).isEqualTo("pending");
  }

  @Test
  void claimCapturesTheDatasourceVersion() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-pending", "pending", now.minusMinutes(1));
    insertProcessingOutbox("sync-pending", now.plusMinutes(5));
    OffsetDateTime datasourceUpdatedAt = datasourceUpdatedAt();

    assertThat(store.claimForExecution(TENANT_ID, DATASOURCE_ID, "sync-pending", "claim-a"))
        .isEqualTo(SyncRunClaimResult.ACQUIRED);

    assertThat(datasourceVersionOfRun("sync-pending")).isEqualTo(datasourceUpdatedAt);
  }

  @Test
  void changedDatasourceInvalidatesTheRunningClaim() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-pending", "pending", now.minusMinutes(1));
    insertProcessingOutbox("sync-pending", now.plusMinutes(5));
    assertThat(store.claimForExecution(TENANT_ID, DATASOURCE_ID, "sync-pending", "claim-a"))
        .isEqualTo(SyncRunClaimResult.ACQUIRED);
    jdbc.update(
        "update datasource set config_json='{\"endpoint\":\"https://new.example\"}'::jsonb, updated_at=updated_at + interval '1 second' where tenant_id=? and id=?",
        TENANT_ID,
        DATASOURCE_ID);

    assertThat(
            store.renewClaim(
                TENANT_ID, DATASOURCE_ID, "sync-pending", "claim-a", now.plusMinutes(5)))
        .isFalse();
    assertThat(
            store.completeClaimed(
                TENANT_ID, DATASOURCE_ID, "sync-pending", "claim-a", java.util.Map.of()))
        .isFalse();
    assertThat(statusOf("sync-pending")).isEqualTo("running");
  }

  @Test
  void expiredClaimCannotRenewOrCompleteBeforeAnotherWorkerReclaimsIt() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-pending", "pending", now.minusMinutes(1));
    insertProcessingOutbox("sync-pending", now.plusMinutes(5));
    assertThat(store.claimForExecution(TENANT_ID, DATASOURCE_ID, "sync-pending", "claim-a"))
        .isEqualTo(SyncRunClaimResult.ACQUIRED);
    jdbc.update(
        "update datasource_sync_run set lease_until = now() - interval '1 second' where id = ?",
        "sync-pending");

    assertThat(
            store.renewClaim(
                TENANT_ID, DATASOURCE_ID, "sync-pending", "claim-a", now.plusMinutes(5)))
        .isFalse();
    assertThat(
            store.completeClaimed(
                TENANT_ID, DATASOURCE_ID, "sync-pending", "claim-a", java.util.Map.of()))
        .isFalse();
    assertThat(statusOf("sync-pending")).isEqualTo("running");
  }

  @Test
  void inactiveTenantInvalidatesTheRunningClaim() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-pending", "pending", now.minusMinutes(1));
    insertProcessingOutbox("sync-pending", now.plusMinutes(5));
    assertThat(store.claimForExecution(TENANT_ID, DATASOURCE_ID, "sync-pending", "claim-a"))
        .isEqualTo(SyncRunClaimResult.ACQUIRED);
    jdbc.update("update tenant set status='inactive' where id=?", TENANT_ID);

    assertThat(
            store.renewClaim(
                TENANT_ID, DATASOURCE_ID, "sync-pending", "claim-a", now.plusMinutes(5)))
        .isFalse();
  }

  @Test
  void datasourceInErrorCanStillClaimItsRetry() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-failed", "failed", now.minusMinutes(1));
    insertProcessingOutbox("sync-failed", now.plusMinutes(5));
    jdbc.update(
        "update datasource set status='error' where tenant_id=? and id=?",
        TENANT_ID,
        DATASOURCE_ID);

    assertThat(store.claimForExecution(TENANT_ID, DATASOURCE_ID, "sync-failed", "claim-a"))
        .isEqualTo(SyncRunClaimResult.ACQUIRED);
  }

  @Test
  void successfulCompletionCannotReactivateDatasourceDisabledAfterClaim() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-pending", "pending", now.minusMinutes(1));
    insertProcessingOutbox("sync-pending", now.plusMinutes(5));
    assertThat(store.claimForExecution(TENANT_ID, DATASOURCE_ID, "sync-pending", "claim-a"))
        .isEqualTo(SyncRunClaimResult.ACQUIRED);
    jdbc.update(
        "update datasource set status='inactive' where tenant_id=? and id=?",
        TENANT_ID,
        DATASOURCE_ID);

    assertThat(
            store.completeClaimed(
                TENANT_ID, DATASOURCE_ID, "sync-pending", "claim-a", java.util.Map.of()))
        .isFalse();
    assertThat(datasourceStatus()).isEqualTo("inactive");
    assertThat(statusOf("sync-pending")).isEqualTo("running");
  }

  @Test
  void failedCompletionCannotOverwriteDatasourceDisabledAfterClaim() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    insertRun("sync-pending", "pending", now.minusMinutes(1));
    insertProcessingOutbox("sync-pending", now.plusMinutes(5));
    assertThat(store.claimForExecution(TENANT_ID, DATASOURCE_ID, "sync-pending", "claim-a"))
        .isEqualTo(SyncRunClaimResult.ACQUIRED);
    jdbc.update(
        "update datasource set status='inactive' where tenant_id=? and id=?",
        TENANT_ID,
        DATASOURCE_ID);

    assertThat(
            store.failClaimed(
                TENANT_ID, DATASOURCE_ID, "sync-pending", "claim-a", java.util.Map.of(), "timeout"))
        .isFalse();
    assertThat(datasourceStatus()).isEqualTo("inactive");
    assertThat(statusOf("sync-pending")).isEqualTo("running");
  }

  private SyncRunClaimResult claimWhenStarted(
      String runId, String claimToken, CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    ready.countDown();
    start.await();
    return transactions.execute(
        status -> store.claimForExecution(TENANT_ID, DATASOURCE_ID, runId, claimToken));
  }
}

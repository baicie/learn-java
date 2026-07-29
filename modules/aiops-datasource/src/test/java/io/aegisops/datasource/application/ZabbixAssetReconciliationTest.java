package io.aegisops.datasource.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.alert.AlertIngestRequest;
import io.aegisops.alert.AlertIngestResult;
import io.aegisops.alert.AlertIngestService;
import io.aegisops.alert.application.AlertAssetReconciliationApplicationService;
import io.aegisops.asset.api.dto.AssetUpsertCommand;
import io.aegisops.asset.api.dto.AssetUpsertResult;
import io.aegisops.asset.application.AssetApplicationService;
import io.aegisops.datasource.application.port.DataSourceSyncStore;
import io.aegisops.datasource.application.port.DataSourceSyncStore.SyncRunClaimResult;
import io.aegisops.datasource.zabbix.ZabbixSyncMapper;
import io.aegisops.incident.application.IncidentAssetReconciliationApplicationService;
import io.aegisops.zabbix.ZabbixClient;
import io.aegisops.zabbix.ZabbixClientFactory;
import io.aegisops.zabbix.ZabbixConfig;
import io.aegisops.zabbix.ZabbixHost;
import io.aegisops.zabbix.ZabbixProblem;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

class ZabbixAssetReconciliationTest {
  @Test
  void mapsHostIntoUnifiedAssetUpsertScopedByDatasourceInstance() {
    DataSourceSyncStore syncStore = mock(DataSourceSyncStore.class);
    ZabbixClientFactory factory = mock(ZabbixClientFactory.class);
    ZabbixClient client = mock(ZabbixClient.class);
    AssetApplicationService assets = mock(AssetApplicationService.class);
    AlertAssetReconciliationApplicationService alertAssets =
        mock(AlertAssetReconciliationApplicationService.class);
    IncidentAssetReconciliationApplicationService incidentAssets =
        mock(IncidentAssetReconciliationApplicationService.class);
    when(syncStore.claimForExecution(eq("tenant-1"), eq("ds-1"), eq("sync-1"), anyString()))
        .thenReturn(SyncRunClaimResult.ACQUIRED);
    when(syncStore.completeClaimed(eq("tenant-1"), eq("ds-1"), eq("sync-1"), anyString(), any()))
        .thenReturn(true);
    when(syncStore.renewClaim(eq("tenant-1"), eq("ds-1"), eq("sync-1"), anyString(), any()))
        .thenReturn(true);
    when(syncStore.loadZabbixConfig("tenant-1", "ds-1"))
        .thenReturn(
            new ZabbixConfig("https://zabbix.example/api_jsonrpc.php", null, null, "secret", 3, 5));
    when(factory.create(any(ZabbixConfig.class))).thenReturn(client);
    when(client.getHosts(1000))
        .thenReturn(
            List.of(
                new ZabbixHost(
                    "10084",
                    "db-prod-1",
                    "DB Prod 1",
                    "0",
                    "10.0.0.8",
                    List.of(),
                    "machine-001",
                    null)));
    when(client.getProblems(1000)).thenReturn(List.of());
    when(assets.upsert(any()))
        .thenReturn(new AssetUpsertResult("asset-1", "source-1", "created", false));
    when(assets.markMissing(eq("tenant-1"), eq("zabbix"), eq("ds-1"), any())).thenReturn(0);
    var service =
        new DataSourceSyncApplicationService(
            syncStore,
            factory,
            new ZabbixSyncMapper(),
            new DataSourceSyncWriteService(
                new DataSourceSyncWriteFence(syncStore, TransactionOperations.withoutTransaction()),
                assets,
                mock(AlertIngestService.class),
                alertAssets,
                incidentAssets),
            new ObjectMapper());

    service.execute("tenant-1", "ds-1", "sync-1");

    verify(syncStore).claimForExecution(eq("tenant-1"), eq("ds-1"), eq("sync-1"), anyString());
    ArgumentCaptor<AssetUpsertCommand> command = ArgumentCaptor.forClass(AssetUpsertCommand.class);
    verify(assets).upsert(command.capture());
    assertThat(command.getValue().sourceType()).isEqualTo("zabbix");
    assertThat(command.getValue().sourceInstanceId()).isEqualTo("ds-1");
    assertThat(command.getValue().externalId()).isEqualTo("10084");
    assertThat(command.getValue().identities()).hasSize(1);
    assertThat(command.getValue().identities().getFirst().identityType()).isEqualTo("machine_id");
    assertThat(command.getValue().identities().getFirst().identityValue()).isEqualTo("machine-001");
    assertThat(command.getValue().identities().getFirst().scopeKey()).isEqualTo("global");
    verify(alertAssets).backfillZabbixAssetId("tenant-1", "ds-1", "10084", "asset-1");
    verify(incidentAssets).backfillPrimaryAssetIds("tenant-1");
  }

  @Test
  void mapsRecoveredProblemIntoRecoveredAlertWithRecoveryTimestamp() {
    DataSourceSyncStore syncStore = mock(DataSourceSyncStore.class);
    ZabbixClientFactory factory = mock(ZabbixClientFactory.class);
    ZabbixClient client = mock(ZabbixClient.class);
    AssetApplicationService assets = mock(AssetApplicationService.class);
    AlertIngestService alerts = mock(AlertIngestService.class);
    AlertAssetReconciliationApplicationService alertAssets =
        mock(AlertAssetReconciliationApplicationService.class);
    IncidentAssetReconciliationApplicationService incidentAssets =
        mock(IncidentAssetReconciliationApplicationService.class);
    Instant recoveredAt = Instant.parse("2026-07-27T05:20:00Z");
    var raw =
        new ObjectMapper()
            .createObjectNode()
            .put("r_eventid", "20002")
            .put("r_clock", recoveredAt.getEpochSecond());

    when(syncStore.claimForExecution(eq("tenant-1"), eq("ds-1"), eq("sync-1"), anyString()))
        .thenReturn(SyncRunClaimResult.ACQUIRED);
    when(syncStore.completeClaimed(eq("tenant-1"), eq("ds-1"), eq("sync-1"), anyString(), any()))
        .thenReturn(true);
    when(syncStore.renewClaim(eq("tenant-1"), eq("ds-1"), eq("sync-1"), anyString(), any()))
        .thenReturn(true);
    when(syncStore.loadZabbixConfig("tenant-1", "ds-1"))
        .thenReturn(
            new ZabbixConfig("https://zabbix.example/api_jsonrpc.php", null, null, "secret", 3, 5));
    when(factory.create(any(ZabbixConfig.class))).thenReturn(client);
    when(client.getHosts(1000)).thenReturn(List.of());
    when(client.getProblems(1000))
        .thenReturn(
            List.of(
                new ZabbixProblem(
                    "20001",
                    "30001",
                    "CPU high",
                    4,
                    Instant.parse("2026-07-27T05:10:00Z"),
                    List.of("10084"),
                    Map.of("env", "demo", "service", "order-service"),
                    raw)));
    when(assets.markMissing(eq("tenant-1"), eq("zabbix"), eq("ds-1"), any())).thenReturn(0);
    when(alerts.ingest(eq("tenant-1"), any(AlertIngestRequest.class), anyString(), anyString()))
        .thenReturn(new AlertIngestResult("alert-1", false, "fp-1", "agg-1"));
    var service =
        new DataSourceSyncApplicationService(
            syncStore,
            factory,
            new ZabbixSyncMapper(),
            new DataSourceSyncWriteService(
                new DataSourceSyncWriteFence(syncStore, TransactionOperations.withoutTransaction()),
                assets,
                alerts,
                alertAssets,
                incidentAssets),
            new ObjectMapper());

    service.execute("tenant-1", "ds-1", "sync-1");

    ArgumentCaptor<AlertIngestRequest> request = ArgumentCaptor.forClass(AlertIngestRequest.class);
    ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> aggregationKey = ArgumentCaptor.forClass(String.class);
    verify(alerts)
        .ingest(eq("tenant-1"), request.capture(), fingerprint.capture(), aggregationKey.capture());
    assertThat(request.getValue().status()).isEqualTo("recovered");
    assertThat(request.getValue().endsAt())
        .isEqualTo(recoveredAt.atOffset(java.time.ZoneOffset.UTC));
    assertThat(request.getValue().rawPayload()).containsEntry("r_eventid", "20002");
    assertThat(((Number) request.getValue().rawPayload().get("r_clock")).longValue())
        .isEqualTo(recoveredAt.getEpochSecond());
    assertThat(request.getValue().labels()).containsEntry("zabbixRecoveryEventId", "20002");
    assertThat(fingerprint.getValue()).isEqualTo("zabbix:ds-1:30001");
    assertThat(aggregationKey.getValue())
        .isEqualTo("zabbix:ds-1:10084:order-service:demo:202607270510");
  }
}

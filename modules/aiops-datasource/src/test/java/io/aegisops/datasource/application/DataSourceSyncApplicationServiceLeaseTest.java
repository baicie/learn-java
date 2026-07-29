package io.aegisops.datasource.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.alert.AlertIngestService;
import io.aegisops.alert.application.AlertAssetReconciliationApplicationService;
import io.aegisops.asset.application.AssetApplicationService;
import io.aegisops.common.exception.AppException;
import io.aegisops.datasource.application.port.DataSourceSyncStore;
import io.aegisops.datasource.application.port.DataSourceSyncStore.SyncRunClaimResult;
import io.aegisops.datasource.zabbix.ZabbixSyncMapper;
import io.aegisops.incident.application.IncidentAssetReconciliationApplicationService;
import io.aegisops.zabbix.ZabbixClient;
import io.aegisops.zabbix.ZabbixClientFactory;
import io.aegisops.zabbix.ZabbixConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

class DataSourceSyncApplicationServiceLeaseTest {
  private final DataSourceSyncStore syncStore = mock(DataSourceSyncStore.class);
  private final ZabbixClientFactory clientFactory = mock(ZabbixClientFactory.class);
  private final AssetApplicationService assetService = mock(AssetApplicationService.class);
  private final AlertIngestService alertService = mock(AlertIngestService.class);
  private final AlertAssetReconciliationApplicationService alertAssetReconciliationService =
      mock(AlertAssetReconciliationApplicationService.class);
  private final IncidentAssetReconciliationApplicationService incidentAssetReconciliationService =
      mock(IncidentAssetReconciliationApplicationService.class);
  private final DataSourceSyncApplicationService service =
      new DataSourceSyncApplicationService(
          syncStore,
          clientFactory,
          new ZabbixSyncMapper(),
          new DataSourceSyncWriteService(
              new DataSourceSyncWriteFence(syncStore, TransactionOperations.withoutTransaction()),
              assetService,
              alertService,
              alertAssetReconciliationService,
              incidentAssetReconciliationService),
          new ObjectMapper());

  @Test
  void treatsSuccessfulRunRedeliveryAsIdempotentSuccess() {
    when(syncStore.claimForExecution(
            org.mockito.ArgumentMatchers.eq("tenant-a"),
            org.mockito.ArgumentMatchers.eq("ds-a"),
            org.mockito.ArgumentMatchers.eq("sync-a"),
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(SyncRunClaimResult.ALREADY_COMPLETED);

    assertThatCode(() -> service.execute("tenant-a", "ds-a", "sync-a")).doesNotThrowAnyException();

    verifyNoInteractions(clientFactory, assetService, alertService);
    verify(syncStore, never())
        .completeClaimed(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyMap());
  }

  @Test
  void rejectsRedeliveryWhileTheExistingRunLeaseIsActive() {
    when(syncStore.claimForExecution(
            org.mockito.ArgumentMatchers.eq("tenant-a"),
            org.mockito.ArgumentMatchers.eq("ds-a"),
            org.mockito.ArgumentMatchers.eq("sync-a"),
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(SyncRunClaimResult.ACTIVE);

    assertThatThrownBy(() -> service.execute("tenant-a", "ds-a", "sync-a"))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("already running");

    verifyNoInteractions(clientFactory, assetService, alertService);
  }

  @Test
  void usesTheClaimTokenToFenceSuccessfulCompletion() {
    ZabbixClient client = mock(ZabbixClient.class);
    when(syncStore.claimForExecution(
            org.mockito.ArgumentMatchers.eq("tenant-a"),
            org.mockito.ArgumentMatchers.eq("ds-a"),
            org.mockito.ArgumentMatchers.eq("sync-a"),
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(SyncRunClaimResult.ACQUIRED);
    when(syncStore.loadZabbixConfig("tenant-a", "ds-a"))
        .thenReturn(new ZabbixConfig("https://zabbix.example", null, null, "token", 3, 5));
    when(syncStore.renewClaim(
            org.mockito.ArgumentMatchers.eq("tenant-a"),
            org.mockito.ArgumentMatchers.eq("ds-a"),
            org.mockito.ArgumentMatchers.eq("sync-a"),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(true);
    when(clientFactory.create(org.mockito.ArgumentMatchers.any())).thenReturn(client);
    when(client.getHosts(1000)).thenReturn(List.of());
    when(client.getProblems(1000)).thenReturn(List.of());
    when(assetService.markMissing(
            org.mockito.ArgumentMatchers.eq("tenant-a"),
            org.mockito.ArgumentMatchers.eq("zabbix"),
            org.mockito.ArgumentMatchers.eq("ds-a"),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(0);
    when(syncStore.completeClaimed(
            org.mockito.ArgumentMatchers.eq("tenant-a"),
            org.mockito.ArgumentMatchers.eq("ds-a"),
            org.mockito.ArgumentMatchers.eq("sync-a"),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyMap()))
        .thenReturn(true);

    service.execute("tenant-a", "ds-a", "sync-a");

    ArgumentCaptor<String> claimedToken = ArgumentCaptor.forClass(String.class);
    verify(syncStore)
        .claimForExecution(
            org.mockito.ArgumentMatchers.eq("tenant-a"),
            org.mockito.ArgumentMatchers.eq("ds-a"),
            org.mockito.ArgumentMatchers.eq("sync-a"),
            claimedToken.capture());
    verify(syncStore)
        .completeClaimed(
            "tenant-a",
            "ds-a",
            "sync-a",
            claimedToken.getValue(),
            Map.of(
                "hostsCreated", 0,
                "hostsUpdated", 0,
                "hostsMissing", 0,
                "alertsCreated", 0,
                "alertsUpdated", 0));
    verify(syncStore, atLeast(4))
        .renewClaim(
            org.mockito.ArgumentMatchers.eq("tenant-a"),
            org.mockito.ArgumentMatchers.eq("ds-a"),
            org.mockito.ArgumentMatchers.eq("sync-a"),
            org.mockito.ArgumentMatchers.eq(claimedToken.getValue()),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void stopsBeforeLoadingZabbixWhenTheClaimCannotBeRenewed() {
    when(syncStore.claimForExecution(
            org.mockito.ArgumentMatchers.eq("tenant-a"),
            org.mockito.ArgumentMatchers.eq("ds-a"),
            org.mockito.ArgumentMatchers.eq("sync-a"),
            org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(SyncRunClaimResult.ACQUIRED);
    when(syncStore.renewClaim(
            org.mockito.ArgumentMatchers.eq("tenant-a"),
            org.mockito.ArgumentMatchers.eq("ds-a"),
            org.mockito.ArgumentMatchers.eq("sync-a"),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(false);

    assertThatThrownBy(() -> service.execute("tenant-a", "ds-a", "sync-a"))
        .isInstanceOf(AppException.class)
        .extracting(error -> ((AppException) error).errorCode())
        .isEqualTo("DATASOURCE_SYNC_CLAIM_LOST");

    verifyNoInteractions(clientFactory, assetService, alertService);
    verify(syncStore, never())
        .failClaimed(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyMap(),
            org.mockito.ArgumentMatchers.anyString());
  }
}

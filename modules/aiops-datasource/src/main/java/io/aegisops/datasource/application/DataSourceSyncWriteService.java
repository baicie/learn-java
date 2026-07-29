package io.aegisops.datasource.application;

import io.aegisops.alert.AlertIngestRequest;
import io.aegisops.alert.AlertIngestResult;
import io.aegisops.alert.AlertIngestService;
import io.aegisops.alert.application.AlertAssetReconciliationApplicationService;
import io.aegisops.asset.api.dto.AssetUpsertCommand;
import io.aegisops.asset.api.dto.AssetUpsertResult;
import io.aegisops.asset.application.AssetApplicationService;
import io.aegisops.incident.application.IncidentAssetReconciliationApplicationService;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

@Service
public class DataSourceSyncWriteService {
  private final DataSourceSyncWriteFence writeFence;
  private final AssetApplicationService assetService;
  private final AlertIngestService alertService;
  private final AlertAssetReconciliationApplicationService alertAssetReconciliationService;
  private final IncidentAssetReconciliationApplicationService incidentAssetReconciliationService;

  public DataSourceSyncWriteService(
      DataSourceSyncWriteFence writeFence,
      AssetApplicationService assetService,
      AlertIngestService alertService,
      AlertAssetReconciliationApplicationService alertAssetReconciliationService,
      IncidentAssetReconciliationApplicationService incidentAssetReconciliationService) {
    this.writeFence = writeFence;
    this.assetService = assetService;
    this.alertService = alertService;
    this.alertAssetReconciliationService = alertAssetReconciliationService;
    this.incidentAssetReconciliationService = incidentAssetReconciliationService;
  }

  AssetUpsertResult upsertHost(
      DataSourceSyncClaim claim, String hostId, AssetUpsertCommand command) {
    return writeFence.executeClaimedWrite(
        claim.tenantId(),
        claim.datasourceId(),
        claim.runId(),
        claim.claimToken(),
        () -> {
          AssetUpsertResult result = assetService.upsert(command);
          alertAssetReconciliationService.backfillZabbixAssetId(
              claim.tenantId(), claim.datasourceId(), hostId, result.assetId());
          return result;
        });
  }

  int markMissingHosts(DataSourceSyncClaim claim, OffsetDateTime syncStarted) {
    return writeFence.executeClaimedWrite(
        claim.tenantId(),
        claim.datasourceId(),
        claim.runId(),
        claim.claimToken(),
        () ->
            assetService.markMissing(
                claim.tenantId(), "zabbix", claim.datasourceId(), syncStarted));
  }

  void reconcileIncidentAssets(DataSourceSyncClaim claim) {
    writeFence.executeClaimedWrite(
        claim.tenantId(),
        claim.datasourceId(),
        claim.runId(),
        claim.claimToken(),
        () -> {
          incidentAssetReconciliationService.backfillPrimaryAssetIds(claim.tenantId());
          return null;
        });
  }

  AlertIngestResult ingestAlert(
      DataSourceSyncClaim claim,
      AlertIngestRequest request,
      String fingerprint,
      String aggregationKey) {
    return writeFence.executeClaimedWrite(
        claim.tenantId(),
        claim.datasourceId(),
        claim.runId(),
        claim.claimToken(),
        () -> alertService.ingest(claim.tenantId(), request, fingerprint, aggregationKey));
  }
}

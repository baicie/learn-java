package io.aegisops.alert.application;

import io.aegisops.alert.infrastructure.persistence.AlertAssetReconciliationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertAssetReconciliationApplicationService {
  private final AlertAssetReconciliationRepository repository;

  public AlertAssetReconciliationApplicationService(AlertAssetReconciliationRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public int backfillZabbixAssetId(
      String tenantId, String datasourceId, String externalHostId, String assetId) {
    requireText(tenantId, "tenantId");
    requireText(datasourceId, "datasourceId");
    requireText(externalHostId, "externalHostId");
    requireText(assetId, "assetId");
    return repository.backfillZabbixAssetId(
        tenantId.trim(), datasourceId.trim(), externalHostId.trim(), assetId.trim());
  }

  private void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }
}

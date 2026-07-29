package io.aegisops.incident.application;

import io.aegisops.incident.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentAssetReconciliationApplicationService {
  private final IncidentRepository repository;

  public IncidentAssetReconciliationApplicationService(IncidentRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public int backfillPrimaryAssetIds(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId is required");
    }
    return repository.backfillPrimaryAssetIds(tenantId.trim());
  }
}

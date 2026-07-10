package io.aegisops.audit;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {
  private static final int DEFAULT_LIMIT = 100;
  private static final int MAX_LIMIT = 200;

  private final AuditRepository repository;

  public AuditQueryService(AuditRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<AuditEvent> listRecent(String tenantId) {
    return repository.listRecent(tenantId, DEFAULT_LIMIT);
  }

  @Transactional(readOnly = true)
  public List<AuditEvent> listByResource(
      String tenantId, String resourceType, String resourceId, Integer requestedLimit) {
    requireText(tenantId, "tenantId");
    requireText(resourceType, "resourceType");
    requireText(resourceId, "resourceId");

    int limit =
        requestedLimit == null ? DEFAULT_LIMIT : Math.min(Math.max(requestedLimit, 1), MAX_LIMIT);

    return repository.listByResource(tenantId, resourceType, resourceId, limit);
  }

  private void requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
  }
}

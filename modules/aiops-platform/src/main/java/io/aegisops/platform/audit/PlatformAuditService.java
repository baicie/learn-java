package io.aegisops.platform.audit;

import io.aegisops.audit.AuditJson;
import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Convenience facade that lets platform services emit change-tracked audit events without manually
 * serializing before/after/detail JSON. The underlying {@link AuditService} is transactional, so
 * callers should already be inside a transactional context.
 */
@Service
public class PlatformAuditService {
  private final AuditService auditService;
  private final AuditJson auditJson;

  public PlatformAuditService(AuditService auditService, AuditJson auditJson) {
    this.auditService = auditService;
    this.auditJson = auditJson;
  }

  public void recordChange(
      String tenantId,
      String actorId,
      String action,
      String resourceType,
      String resourceId,
      Object... change) {
    if (change.length != 3 || !(change[2] instanceof Map<?, ?>)) {
      throw new IllegalArgumentException("audit change requires before, after and attributes");
    }
    Object before = change[0];
    Object after = change[1];
    @SuppressWarnings("unchecked")
    Map<String, Object> attributes = (Map<String, Object>) change[2];
    auditService.record(
        new AuditRecordCommand(
            tenantId,
            actorId,
            action,
            resourceType,
            resourceId,
            auditJson.write(before),
            auditJson.write(after),
            auditJson.detail(attributes, before, after)));
  }
}

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
      Object before,
      Object after,
      Map<String, Object> attributes) {
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

package io.aegisops.workrecord.application.service;

import io.aegisops.audit.AuditJson;
import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WorkRecordAuditService {
  private final AuditService auditService;
  private final AuditJson auditJson;

  public WorkRecordAuditService(AuditService auditService, AuditJson auditJson) {
    this.auditService = auditService;
    this.auditJson = auditJson;
  }

  /**
   * Compatibility entry point used by export service and other call sites that only need a detail
   * JSON without a snapshot diff.
   */
  public void record(
      String tenantId,
      String recordId,
      String templateId,
      String resourceType,
      String resourceId,
      String action,
      String actorId,
      String detailJson) {
    Map<String, Object> detail = new LinkedHashMap<>();
    if (recordId != null && !recordId.isBlank()) {
      detail.put("recordId", recordId);
    }
    if (templateId != null && !templateId.isBlank()) {
      detail.put("templateId", templateId);
    }

    String computedDetail =
        detailJson == null || detailJson.isBlank()
            ? auditJson.detail(detail, Map.of(), Map.of())
            : detailJson;

    auditService.record(
        new AuditRecordCommand(
            tenantId, actorId, action, resourceType, resourceId, "{}", "{}", computedDetail));
  }

  public void recordChange(
      String tenantId,
      String recordId,
      String templateId,
      String resourceType,
      String resourceId,
      String action,
      String actorId,
      Object before,
      Object after,
      Map<String, Object> attributes) {
    Map<String, Object> detail = new LinkedHashMap<>();

    if (attributes != null) {
      detail.putAll(attributes);
    }

    if (recordId != null && !recordId.isBlank()) {
      detail.put("recordId", recordId);
    }

    if (templateId != null && !templateId.isBlank()) {
      detail.put("templateId", templateId);
    }

    auditService.record(
        new AuditRecordCommand(
            tenantId,
            actorId,
            action,
            resourceType,
            resourceId,
            auditJson.write(before),
            auditJson.write(after),
            auditJson.detail(detail, before, after)));
  }
}

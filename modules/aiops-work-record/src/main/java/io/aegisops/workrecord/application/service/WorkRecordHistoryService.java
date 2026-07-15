package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.aegisops.audit.AuditEvent;
import io.aegisops.audit.AuditJson;
import io.aegisops.audit.AuditQueryService;
import io.aegisops.security.UserPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the change history of a single work record by reading the unified {@code audit_log}
 * filtered to {@code resourceType = "work_record"}. The caller is responsible for verifying the
 * user can actually read the record before invoking this service.
 */
@Service
public class WorkRecordHistoryService {
  private static final int HISTORY_LIMIT = 100;
  private static final String RECORD_RESOURCE_TYPE = "work_record";

  private final AuditQueryService auditQueryService;
  private final AuditJson auditJson;
  private final FieldPolicyService fieldPolicies;
  private final ObjectMapper objectMapper;

  public WorkRecordHistoryService(
      AuditQueryService auditQueryService,
      AuditJson auditJson,
      FieldPolicyService fieldPolicies,
      ObjectMapper objectMapper) {
    this.auditQueryService = auditQueryService;
    this.auditJson = auditJson;
    this.fieldPolicies = fieldPolicies;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public List<AuditEvent> list(
      String tenantId, String recordId, String templateVersionId, UserPrincipal principal) {
    return auditQueryService
        .listByResource(tenantId, RECORD_RESOURCE_TYPE, recordId, HISTORY_LIMIT)
        .stream()
        .map(event -> filter(event, tenantId, templateVersionId, principal))
        .toList();
  }

  private AuditEvent filter(
      AuditEvent event, String tenantId, String templateVersionId, UserPrincipal principal) {
    String before =
        fieldPolicies.filterAuditSnapshot(
            tenantId, templateVersionId, event.beforeJson(), principal);
    String after =
        fieldPolicies.filterAuditSnapshot(
            tenantId, templateVersionId, event.afterJson(), principal);
    return new AuditEvent(
        event.id(),
        event.tenantId(),
        event.actorId(),
        event.action(),
        event.resourceType(),
        event.resourceId(),
        before,
        after,
        auditJson.detail(detailAttributes(event.detailJson()), node(before), node(after)),
        event.createdAt());
  }

  private Map<String, Object> detailAttributes(String json) {
    ObjectNode detail = (ObjectNode) node(json);
    detail.remove(List.of("changes", "changesTruncated"));
    return objectMapper.convertValue(
        detail, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
  }

  private JsonNode node(String json) {
    try {
      JsonNode value = objectMapper.readTree(json == null ? "{}" : json);
      return value != null && value.isObject() ? value : objectMapper.createObjectNode();
    } catch (Exception ignored) {
      return objectMapper.createObjectNode();
    }
  }
}

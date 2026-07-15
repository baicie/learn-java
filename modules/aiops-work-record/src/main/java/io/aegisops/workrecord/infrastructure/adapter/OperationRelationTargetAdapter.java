package io.aegisops.workrecord.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.alert.AlertQueryService;
import io.aegisops.incident.IncidentService;
import io.aegisops.inspection.InspectionService;
import io.aegisops.security.PermissionCodes;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.RelationTargetPort;
import io.aegisops.workrecord.domain.model.RelationType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class OperationRelationTargetAdapter implements RelationTargetPort {
  private final AlertQueryService alerts;
  private final IncidentService incidents;
  private final InspectionService inspections;
  private final ObjectMapper objectMapper;

  public OperationRelationTargetAdapter(
      AlertQueryService alerts,
      IncidentService incidents,
      InspectionService inspections,
      ObjectMapper objectMapper) {
    this.alerts = alerts;
    this.incidents = incidents;
    this.inspections = inspections;
    this.objectMapper = objectMapper;
  }

  @Override
  public ResolvedTarget resolve(
      String tenantId, RelationType type, String targetId, UserPrincipal principal) {
    String permission =
        type == RelationType.ALERT ? PermissionCodes.ALERT_READ : PermissionCodes.INCIDENT_READ;
    if (principal == null
        || !tenantId.equals(principal.tenantId())
        || !principal.hasPermission(permission)) {
      throw new AccessDeniedException("not allowed to read relation target");
    }
    return switch (type) {
      case ALERT -> {
        var target = alerts.get(tenantId, targetId);
        yield target(
            target.id(),
            target.title(),
            target.status(),
            snapshot("severity", target.severity(), "startsAt", target.startsAt()));
      }
      case INCIDENT -> {
        var target = incidents.detail(tenantId, targetId).incident();
        yield target(
            target.id(),
            target.title(),
            target.status(),
            snapshot("severity", target.severity(), "startedAt", target.startedAt()));
      }
      case INSPECTION -> {
        var target = inspections.get(tenantId, targetId);
        yield target(
            target.id(),
            target.name(),
            target.enabled() ? "enabled" : "disabled",
            snapshot("targetType", target.targetType(), "templateKey", target.templateKey()));
      }
    };
  }

  private ResolvedTarget target(
      String id, String title, String status, Map<String, Object> snapshot) {
    try {
      return new ResolvedTarget(id, title, status, objectMapper.writeValueAsString(snapshot));
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("failed to serialize relation snapshot", ex);
    }
  }

  private static Map<String, Object> snapshot(
      String firstKey, Object firstValue, String secondKey, Object secondValue) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put(firstKey, firstValue);
    snapshot.put(secondKey, secondValue);
    return snapshot;
  }
}

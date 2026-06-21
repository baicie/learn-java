package io.aegisops.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

public record SecurityAuditRecord(
    String tenantId,
    String eventType,
    String severity,
    String summary,
    HttpServletRequest request,
    Map<String, Object> metadata) {

  public SecurityAuditRecord {
    if (metadata == null) {
      metadata = Map.of();
    }
  }

  public SecurityAuditRecord(
      String tenantId,
      String eventType,
      String severity,
      String summary,
      HttpServletRequest request) {
    this(tenantId, eventType, severity, summary, request, Map.of());
  }
}

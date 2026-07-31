package io.aegisops.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TenantSecurityAuditService {
  private final TenantSecurityEventRepository repository;
  private final ObjectMapper objectMapper;

  public TenantSecurityAuditService(
      TenantSecurityEventRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  public void record(
      String tenantId,
      String eventType,
      String severity,
      String summary,
      HttpServletRequest request) {
    record(new SecurityAuditRecord(tenantId, eventType, severity, summary, request));
  }

  public void record(String tenantId, String eventType, String severity, String summary) {
    record(new SecurityAuditRecord(tenantId, eventType, severity, summary, null));
  }

  public void record(SecurityAuditRecord audit) {
    try {
      repository.create(
          new TenantSecurityEventCreateCommand(
              newId("tse"),
              audit.tenantId(),
              audit.eventType(),
              audit.severity(),
              actor(audit.request()),
              audit.request() == null ? null : audit.request().getRequestURI(),
              audit.request() == null ? null : remoteAddr(audit.request()),
              audit.summary(),
              objectMapper.writeValueAsString(audit.metadata())));
    } catch (Exception ignored) {
      // Security audit failure must not break request handling.
    }
  }

  private String actor(HttpServletRequest request) {
    if (request != null) {
      Object principal =
          request.getAttribute(SecurityConstants.REQUEST_ATTRIBUTE_SERVICE_PRINCIPAL);
      if (principal instanceof InternalServicePrincipal servicePrincipal) {
        return servicePrincipal.serviceId();
      }
    }
    return "system";
  }

  private String remoteAddr(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}

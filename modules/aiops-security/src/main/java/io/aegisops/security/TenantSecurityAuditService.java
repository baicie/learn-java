package io.aegisops.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TenantSecurityAuditService {
  private final TenantSecurityEventRepository repository;
  private final ObjectMapper objectMapper;

  public TenantSecurityAuditService(
      TenantSecurityEventRepository repository,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  public void record(
      String tenantId,
      String eventType,
      String severity,
      String summary,
      HttpServletRequest request) {
    record(tenantId, eventType, severity, summary, request, Map.of());
  }

  public void record(
      String tenantId,
      String eventType,
      String severity,
      String summary,
      HttpServletRequest request,
      Map<String, Object> metadata) {
    try {
      repository.create(
          new TenantSecurityEventCreateCommand(
              newId("tse"),
              tenantId,
              eventType,
              severity,
              "system",
              request == null ? null : request.getRequestURI(),
              request == null ? null : remoteAddr(request),
              summary,
              objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata)));
    } catch (Exception ignored) {
      // Security audit failure must not break request handling.
    }
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

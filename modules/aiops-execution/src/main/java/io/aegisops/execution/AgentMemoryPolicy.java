package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.AgentMemoryCreateRequest;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AgentMemoryPolicy {
  private static final int MAX_CONTENT_LENGTH = 4000;

  private static final List<String> ALLOWED_MEMORY_TYPES =
      List.of(
          "incident_summary",
          "root_cause_pattern",
          "service_behavior",
          "runbook_hint",
          "safety_note");

  private static final List<String> ALLOWED_SCOPE_TYPES =
      List.of("tenant", "service", "incident", "asset");

  private static final List<String> ALLOWED_SOURCE_TYPES =
      List.of("diagnosis", "postmortem", "incident_case", "manual");

  private static final List<Pattern> SECRET_PATTERNS =
      List.of(
          Pattern.compile("(?i)password\\s*[:=]"),
          Pattern.compile("(?i)secret\\s*[:=]"),
          Pattern.compile("(?i)token\\s*[:=]"),
          Pattern.compile("(?i)api[_-]?key\\s*[:=]"),
          Pattern.compile("-----BEGIN\\s+(RSA|OPENSSH|PRIVATE)\\s+KEY-----"));

  public void validateCreate(AgentMemoryCreateRequest request) {
    if (request == null) {
      throw new AppException("AGENT_MEMORY_REQUEST_REQUIRED", "Memory create request is required");
    }
    if (request.tenantId() == null || request.tenantId().isBlank()) {
      throw new AppException("AGENT_MEMORY_TENANT_REQUIRED", "Tenant id is required");
    }
    if (request.title() == null || request.title().isBlank()) {
      throw new AppException("AGENT_MEMORY_TITLE_REQUIRED", "Memory title is required");
    }
    if (request.content() == null || request.content().isBlank()) {
      throw new AppException("AGENT_MEMORY_CONTENT_REQUIRED", "Memory content is required");
    }
    if (request.content().length() > MAX_CONTENT_LENGTH) {
      throw new AppException("AGENT_MEMORY_CONTENT_TOO_LONG", "Memory content is too long");
    }
    if (!ALLOWED_MEMORY_TYPES.contains(normalize(request.memoryType(), "incident_summary"))) {
      throw new AppException("AGENT_MEMORY_TYPE_INVALID", "Invalid memory type");
    }
    if (!ALLOWED_SCOPE_TYPES.contains(normalize(request.scopeType(), "tenant"))) {
      throw new AppException("AGENT_MEMORY_SCOPE_INVALID", "Invalid memory scope");
    }
    if (!ALLOWED_SOURCE_TYPES.contains(normalize(request.sourceType(), "diagnosis"))) {
      throw new AppException("AGENT_MEMORY_SOURCE_INVALID", "Invalid memory source type");
    }
    if (containsSecret(request.title()) || containsSecret(request.content())) {
      throw new AppException(
          "AGENT_MEMORY_SECRET_REJECTED", "Memory content contains secret-like text");
    }
  }

  public boolean containsSecret(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }

    for (Pattern pattern : SECRET_PATTERNS) {
      if (pattern.matcher(text).find()) {
        return true;
      }
    }

    return false;
  }

  public String normalize(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim().toLowerCase();
  }
}

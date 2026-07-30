package io.aegisops.security;

import jakarta.servlet.http.HttpServletRequest;

final class InternalAgentScopePolicy {
  private InternalAgentScopePolicy() {}

  static String requiredScope(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path.startsWith("/internal/agent/evidence/")) {
      return "evidence:read";
    }
    if (path.equals("/internal/agent/tools/search-cases")) {
      return "cases:read";
    }
    if (path.equals("/internal/agent/plugins/tools/authorize")) {
      return "plugin:authorize";
    }
    if (path.equals("/internal/agent/memories/search")) {
      return "memory:read";
    }
    if (path.equals("/internal/agent/memories")) {
      return "memory:write";
    }
    if (path.equals("/internal/agent/checkpoints")
        || path.startsWith("/internal/agent/checkpoints/")) {
      return "GET".equalsIgnoreCase(request.getMethod()) ? "checkpoint:read" : "checkpoint:write";
    }
    return "internal:agent";
  }
}

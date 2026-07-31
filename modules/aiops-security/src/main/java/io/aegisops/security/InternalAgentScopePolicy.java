package io.aegisops.security;

import jakarta.servlet.http.HttpServletRequest;

final class InternalAgentScopePolicy {
  private InternalAgentScopePolicy() {}

  static String requiredScope(HttpServletRequest request) {
    String path = request.getRequestURI();
    String method = request.getMethod();
    if ("POST".equalsIgnoreCase(method) && path.equals("/internal/agent/evidence/query")) {
      return "evidence:read";
    }
    if ("POST".equalsIgnoreCase(method) && path.equals("/internal/agent/auth/probe")) {
      return "evidence:read";
    }
    if ("POST".equalsIgnoreCase(method) && path.equals("/internal/agent/tools/search-cases")) {
      return "cases:read";
    }
    if ("POST".equalsIgnoreCase(method) && path.equals("/internal/agent/plugins/tools/authorize")) {
      return "plugin:authorize";
    }
    if ("POST".equalsIgnoreCase(method) && path.equals("/internal/agent/memories/search")) {
      return "memory:read";
    }
    if ("POST".equalsIgnoreCase(method) && path.equals("/internal/agent/memories")) {
      return "memory:write";
    }
    if (path.equals("/internal/agent/checkpoints")
        || path.startsWith("/internal/agent/checkpoints/")) {
      if ("GET".equalsIgnoreCase(method)) {
        return "checkpoint:read";
      }
      if ("POST".equalsIgnoreCase(method)
          || "PUT".equalsIgnoreCase(method)
          || "PATCH".equalsIgnoreCase(method)) {
        return "checkpoint:write";
      }
    }
    throw new InternalServiceAuthorizationException(
        "Internal agent endpoint has no explicit scope policy");
  }
}

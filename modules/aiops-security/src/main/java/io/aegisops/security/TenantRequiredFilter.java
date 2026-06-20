package io.aegisops.security;

import io.aegisops.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TenantRequiredFilter extends OncePerRequestFilter {
  private final AiopsSecurityProperties properties;
  private final SecurityErrorResponseWriter responseWriter;
  private final TenantSecurityAuditService auditService;

  public TenantRequiredFilter(
      AiopsSecurityProperties properties,
      SecurityErrorResponseWriter responseWriter,
      TenantSecurityAuditService auditService) {
    this.properties = properties;
    this.responseWriter = responseWriter;
    this.auditService = auditService;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!properties.isTenantRequired()) {
      return true;
    }
    String path = request.getRequestURI();
    return path.equals("/health")
        || path.startsWith("/actuator")
        || path.startsWith("/swagger")
        || path.startsWith("/v3/api-docs")
        || path.startsWith("/error")
        || path.startsWith("/api/auth/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String path = request.getRequestURI();

    if (!path.startsWith("/api/") && !path.startsWith("/internal/agent/")) {
      filterChain.doFilter(request, response);
      return;
    }

    String tenantId = resolveTenantId(request);
    if (tenantId == null || tenantId.isBlank()) {
      auditService.record(
          null,
          "tenant_missing",
          "high",
          "Tenant id is required",
          request);
      responseWriter.write(
          response,
          HttpStatus.BAD_REQUEST.value(),
          "TENANT_REQUIRED",
          "Tenant id is required");
      return;
    }

    TenantContext.setTenantId(tenantId.trim());

    try {
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
    }
  }

  private String resolveTenantId(HttpServletRequest request) {
    String header = request.getHeader(SecurityConstants.HEADER_TENANT_ID);
    if (header != null && !header.isBlank()) {
      return header;
    }

    String parameter = request.getParameter("tenantId");
    if (parameter != null && !parameter.isBlank()) {
      return parameter;
    }

    return TenantContext.getTenantId();
  }
}

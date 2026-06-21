package io.aegisops.observability;

import io.aegisops.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Synchronizes tenant id into MDC after security / tenant filters have had a chance to populate
 * TenantContext.
 *
 * <p>RequestIdFilter still owns requestId / traceId and wraps the whole request. This filter only
 * refreshes tenantId for JWT-derived tenant context.
 */
public class TenantMdcFilter extends OncePerRequestFilter {
  private final ObservabilityProperties properties;

  public TenantMdcFilter(ObservabilityProperties properties) {
    this.properties = properties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !properties.isEnabled() || request.getRequestURI().startsWith("/actuator");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String tenantId =
        firstNonBlank(TenantContext.getTenantId(), request.getHeader("X-Tenant-Id"));
    boolean tenantMdcSet = false;

    if (tenantId != null && !tenantId.isBlank()) {
      MDC.put(ObservabilityConstants.MDC_TENANT_ID, tenantId);
      tenantMdcSet = true;
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      if (tenantMdcSet) {
        MDC.remove(ObservabilityConstants.MDC_TENANT_ID);
      }
    }
  }

  private String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) {
      return first.trim();
    }
    if (second != null && !second.isBlank()) {
      return second.trim();
    }
    return null;
  }
}

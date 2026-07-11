package io.aegisops.security;

import io.aegisops.common.exception.AppException;
import io.aegisops.common.exception.ErrorCode;
import io.aegisops.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public class TenantRateLimitFilter extends OncePerRequestFilter {
  private final AiopsQuotaProperties quotaProperties;
  private final RateLimitService rateLimiter;
  private final SecurityErrorResponseWriter responseWriter;
  private final TenantSecurityAuditService auditService;

  public TenantRateLimitFilter(
      AiopsQuotaProperties quotaProperties,
      RateLimitService rateLimiter,
      SecurityErrorResponseWriter responseWriter,
      TenantSecurityAuditService auditService) {
    this.quotaProperties = quotaProperties;
    this.rateLimiter = rateLimiter;
    this.responseWriter = responseWriter;
    this.auditService = auditService;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!quotaProperties.isRateLimitEnabled()) {
      return true;
    }

    String path = request.getRequestURI();
    return !(path.startsWith("/api/") || path.startsWith("/internal/agent/"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String tenantId = TenantContext.getTenantId();
    if (tenantId == null || tenantId.isBlank()) {
      tenantId = request.getHeader(SecurityConstants.HEADER_TENANT_ID);
    }

    if (tenantId == null || tenantId.isBlank()) {
      filterChain.doFilter(request, response);
      return;
    }

    String path = request.getRequestURI();
    boolean internal = path.startsWith("/internal/agent/");
    int limit =
        internal
            ? quotaProperties.getInternalAgentRequestsPerMinute()
            : quotaProperties.getPublicApiRequestsPerMinute();

    String bucketKey = tenantId + ":" + (internal ? "internal-agent" : "public-api");
    RateLimitDecision decision;
    try {
      decision =
          rateLimiter.acquire(bucketKey, limit, Duration.ofMinutes(1));
    } catch (AppException ex) {
      throw ex;
    }

    if (!decision.allowed()) {
      auditService.record(tenantId, "rate_limited", "high", "Tenant request rate limited", request);
      responseWriter.write(
          response,
          ErrorCode.TENANT_RATE_LIMITED.httpStatus(),
          ErrorCode.TENANT_RATE_LIMITED.name(),
          "Tenant request rate limited");
      return;
    }

    filterChain.doFilter(request, response);
  }
}
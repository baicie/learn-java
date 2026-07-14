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

/**
 * 限流 Filter。所有入站请求按以下顺序确定主体：
 *
 * <ol>
 *   <li>{@code /api/auth/**}：认证路径，必须忽略可伪造的 {@code X-Tenant-Id} 头， 按客户端 IP 计入匿名限流，防止暴力破解登录。
 *   <li>已有 {@link TenantContext}（通常来自内部 Agent 调用）：按租户 + internal/public 桶计数。
 *   <li>其他请求：按请求 IP 计入匿名限流。
 * </ol>
 *
 * <p>限流被拒或后端不可用时，统一通过 {@link SecurityErrorResponseWriter} 以 {@link
 * io.aegisops.common.api.ApiResponse} 契约输出，避免绕过 Controller 的 {@code GlobalExceptionHandler}。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public class TenantRateLimitFilter extends OncePerRequestFilter {

  private static final String AUTH_PATH_PREFIX = "/api/auth/";

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

    RateLimitSubject subject = resolveSubject(request);

    RateLimitDecision decision;
    try {
      decision = rateLimiter.acquire(subject.key(), subject.limit(), Duration.ofMinutes(1));
    } catch (AppException ex) {
      responseWriter.write(
          response, ex.httpStatus(), ex.errorCode(), "rate limit service is unavailable");
      return;
    }

    response.setHeader("RateLimit-Limit", String.valueOf(subject.limit()));
    response.setHeader("RateLimit-Remaining", String.valueOf(decision.remaining()));
    response.setHeader("RateLimit-Reset", String.valueOf(decision.retryAfterSeconds()));

    if (!decision.allowed()) {
      if (subject.tenantId() != null) {
        auditService.record(
            subject.tenantId(), "rate_limited", "high", "Request rate limited", request);
      }

      String errorCode =
          subject.anonymous()
              ? ErrorCode.RATE_LIMITED.name()
              : ErrorCode.TENANT_RATE_LIMITED.name();

      responseWriter.write(
          response,
          ErrorCode.RATE_LIMITED.httpStatus(),
          errorCode,
          "request rate limit exceeded",
          decision.retryAfterSeconds());
      return;
    }

    filterChain.doFilter(request, response);
  }

  private RateLimitSubject resolveSubject(HttpServletRequest request) {
    String path = request.getRequestURI();

    if (path.startsWith(AUTH_PATH_PREFIX)) {
      return new RateLimitSubject(
          "anonymous:auth:" + clientAddress(request),
          quotaProperties.getAnonymousRequestsPerMinute(),
          null,
          true);
    }

    String tenantId = TenantContext.getTenantId();
    if ((tenantId == null || tenantId.isBlank()) && path.startsWith("/internal/agent/")) {
      tenantId = request.getHeader(SecurityConstants.HEADER_TENANT_ID);
    }

    if (tenantId != null && !tenantId.isBlank()) {
      boolean internal = path.startsWith("/internal/agent/");
      return new RateLimitSubject(
          "tenant:" + tenantId + ":" + (internal ? "internal" : "public"),
          internal
              ? quotaProperties.getInternalAgentRequestsPerMinute()
              : quotaProperties.getPublicApiRequestsPerMinute(),
          tenantId,
          false);
    }

    return new RateLimitSubject(
        "anonymous:public:" + clientAddress(request),
        quotaProperties.getAnonymousRequestsPerMinute(),
        null,
        true);
  }

  private String clientAddress(HttpServletRequest request) {
    String address = request.getRemoteAddr();
    return address == null || address.isBlank() ? "unknown" : address;
  }

  private record RateLimitSubject(String key, int limit, String tenantId, boolean anonymous) {}
}

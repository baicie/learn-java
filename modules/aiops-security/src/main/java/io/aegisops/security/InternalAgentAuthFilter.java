package io.aegisops.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class InternalAgentAuthFilter extends OncePerRequestFilter {
  private final AiopsSecurityProperties properties;
  private final SecurityErrorResponseWriter responseWriter;
  private final TenantSecurityAuditService auditService;

  public InternalAgentAuthFilter(
      AiopsSecurityProperties properties,
      SecurityErrorResponseWriter responseWriter,
      TenantSecurityAuditService auditService) {
    this.properties = properties;
    this.responseWriter = responseWriter;
    this.auditService = auditService;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/internal/agent/")
        || !properties.isInternalAgentTokenRequired();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String tenantId = request.getHeader(SecurityConstants.HEADER_TENANT_ID);
    String actualToken = request.getHeader(SecurityConstants.HEADER_INTERNAL_AGENT_TOKEN);
    String expectedToken = properties.getInternalAgentToken();

    if (!ConstantTimeTokenMatcher.matches(expectedToken, actualToken)) {
      auditService.record(
          tenantId,
          "internal_auth_failed",
          "critical",
          "Invalid internal agent token",
          request);
      responseWriter.write(
          response,
          HttpStatus.UNAUTHORIZED.value(),
          "INTERNAL_AGENT_AUTH_FAILED",
          "Invalid internal agent token");
      return;
    }

    filterChain.doFilter(request, response);
  }
}

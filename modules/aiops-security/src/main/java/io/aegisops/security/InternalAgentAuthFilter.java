package io.aegisops.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.security.DiagnosisGrantClaims;
import io.aegisops.common.security.DiagnosisGrantCodec;
import io.aegisops.common.security.InvalidDiagnosisGrantException;
import io.aegisops.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class InternalAgentAuthFilter extends OncePerRequestFilter {
  private final AiopsSecurityProperties properties;
  private final SecurityErrorResponseWriter responseWriter;
  private final TenantSecurityAuditService auditService;
  private final InternalServiceAuthenticator authenticator;
  private final DiagnosisGrantCodec diagnosisGrantCodec;

  public InternalAgentAuthFilter(
      AiopsSecurityProperties properties,
      SecurityErrorResponseWriter responseWriter,
      TenantSecurityAuditService auditService) {
    this(
        properties,
        responseWriter,
        auditService,
        new StaticInternalServiceAuthenticator(properties),
        Clock.systemUTC());
  }

  InternalAgentAuthFilter(
      AiopsSecurityProperties properties,
      SecurityErrorResponseWriter responseWriter,
      TenantSecurityAuditService auditService,
      Clock clock) {
    this(
        properties,
        responseWriter,
        auditService,
        new StaticInternalServiceAuthenticator(properties),
        clock);
  }

  InternalAgentAuthFilter(
      AiopsSecurityProperties properties,
      SecurityErrorResponseWriter responseWriter,
      TenantSecurityAuditService auditService,
      InternalServiceAuthenticator authenticator,
      Clock clock) {
    this.properties = properties;
    this.responseWriter = responseWriter;
    this.auditService = auditService;
    this.authenticator = authenticator;
    this.diagnosisGrantCodec = new DiagnosisGrantCodec(new ObjectMapper(), clock);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/internal/agent/")
        || !properties.isInternalAgentTokenRequired();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String tenantId = request.getHeader(SecurityConstants.HEADER_TENANT_ID);
    try {
      InternalServicePrincipal principal = authenticator.authenticate(request);
      request.setAttribute(SecurityConstants.REQUEST_ATTRIBUTE_SERVICE_PRINCIPAL, principal);
    } catch (InternalServiceAuthenticationException exception) {
      auditService.record(
          tenantId, "internal_auth_failed", "critical", exception.getMessage(), request);
      responseWriter.write(
          response,
          HttpStatus.UNAUTHORIZED.value(),
          "INTERNAL_AGENT_AUTH_FAILED",
          "Invalid internal service credentials");
      return;
    }

    if (!properties.isDiagnosisGrantRequired()) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      DiagnosisGrantClaims claims =
          diagnosisGrantCodec.verify(
              properties.getDiagnosisGrantSecret(),
              request.getHeader(SecurityConstants.HEADER_DIAGNOSIS_GRANT),
              properties.getDiagnosisGrantAudience());
      if (!properties.getDiagnosisGrantIssuers().contains(claims.issuer())) {
        throw new InvalidDiagnosisGrantException("Diagnosis grant issuer is not allowed");
      }

      request.setAttribute(SecurityConstants.REQUEST_ATTRIBUTE_DIAGNOSIS_GRANT, claims);
      TenantContext.setTenantId(claims.tenantId());
      try {
        filterChain.doFilter(request, response);
      } finally {
        TenantContext.clear();
      }
    } catch (InvalidDiagnosisGrantException exception) {
      auditService.record(
          tenantId, "diagnosis_grant_invalid", "critical", exception.getMessage(), request);
      responseWriter.write(
          response,
          HttpStatus.UNAUTHORIZED.value(),
          "DIAGNOSIS_GRANT_INVALID",
          "Invalid diagnosis authorization grant");
    }
  }
}

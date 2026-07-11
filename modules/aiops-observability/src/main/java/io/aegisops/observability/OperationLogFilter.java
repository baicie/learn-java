package io.aegisops.observability;

import io.aegisops.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class OperationLogFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger("AIOPS_OPERATION");

  private static final Set<String> MUTATING_METHODS =
      Set.of("POST", "PUT", "PATCH", "DELETE");

  private final ObservabilityProperties properties;

  public OperationLogFilter(ObservabilityProperties properties) {
    this.properties = properties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!properties.isEnabled() || !properties.isOperationLogEnabled()) {
      return true;
    }
    return request.getRequestURI().startsWith("/actuator");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    long started = System.nanoTime();

    try {
      filterChain.doFilter(request, response);
    } finally {
      int status = response.getStatus();

      boolean shouldLog =
          MUTATING_METHODS.contains(request.getMethod()) || status >= 400;

      if (!shouldLog) {
        return;
      }

      long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

      UserPrincipal principal = principal();

      log.atInfo()
          .addKeyValue("event", "api_operation")
          .addKeyValue("requestId", MDC.get(ObservabilityConstants.MDC_REQUEST_ID))
          .addKeyValue("traceId", MDC.get(ObservabilityConstants.MDC_TRACE_ID))
          .addKeyValue("tenantId", MDC.get(ObservabilityConstants.MDC_TENANT_ID))
          .addKeyValue("userId", principal == null ? null : principal.id())
          .addKeyValue("method", request.getMethod())
          .addKeyValue("path", request.getRequestURI())
          .addKeyValue("status", status)
          .addKeyValue("durationMs", durationMs)
          .addKeyValue("remoteAddress", request.getRemoteAddr())
          .log("API operation");
    }
  }

  private UserPrincipal principal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null) {
      return null;
    }

    return authentication.getPrincipal() instanceof UserPrincipal value ? value : null;
  }
}
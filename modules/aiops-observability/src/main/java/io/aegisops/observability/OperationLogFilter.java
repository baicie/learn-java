package io.aegisops.observability;

import io.aegisops.common.security.AuthenticatedActor;
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

      boolean shouldLog = MUTATING_METHODS.contains(request.getMethod()) || status >= 400;

      if (!shouldLog) {
        return;
      }

      long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
      String userId = currentActorId();

      log.atInfo()
          .addKeyValue("event", "api_operation")
          .addKeyValue("requestId", MDC.get(ObservabilityConstants.MDC_REQUEST_ID))
          .addKeyValue("traceId", MDC.get(ObservabilityConstants.MDC_TRACE_ID))
          .addKeyValue("tenantId", MDC.get(ObservabilityConstants.MDC_TENANT_ID))
          .addKeyValue("userId", userId)
          .addKeyValue("method", request.getMethod())
          .addKeyValue("path", request.getRequestURI())
          .addKeyValue("status", status)
          .addKeyValue("durationMs", durationMs)
          .addKeyValue("remoteAddress", request.getRemoteAddr())
          .log("API operation");
    }
  }

  private String currentActorId() {
    return actorId(SecurityContextHolder.getContext().getAuthentication());
  }

  static String actorId(Authentication authentication) {
    if (authentication == null) {
      return null;
    }

    Object principal = authentication.getPrincipal();
    return principal instanceof AuthenticatedActor actor ? actor.id() : null;
  }
}

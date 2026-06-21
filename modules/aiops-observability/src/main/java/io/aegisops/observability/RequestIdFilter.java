package io.aegisops.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class RequestIdFilter extends OncePerRequestFilter {
  private final ObservabilityProperties properties;

  public RequestIdFilter(ObservabilityProperties properties) {
    this.properties = properties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !properties.isEnabled();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = firstNonBlank(request.getHeader(properties.getRequestIdHeader()), newId());
    String traceId = firstNonBlank(request.getHeader(properties.getTraceIdHeader()), requestId);

    MDC.put(ObservabilityConstants.MDC_REQUEST_ID, requestId);
    MDC.put(ObservabilityConstants.MDC_TRACE_ID, traceId);
    MDC.put(ObservabilityConstants.MDC_SERVICE, properties.getServiceName());

    if (properties.isRequestIdResponseHeaderEnabled()) {
      response.setHeader(properties.getRequestIdHeader(), requestId);
      response.setHeader(properties.getTraceIdHeader(), traceId);
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(ObservabilityConstants.MDC_REQUEST_ID);
      MDC.remove(ObservabilityConstants.MDC_TRACE_ID);
      MDC.remove(ObservabilityConstants.MDC_SERVICE);
    }
  }

  private String firstNonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String newId() {
    return "req_" + UUID.randomUUID().toString().replace("-", "");
  }
}

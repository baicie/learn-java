package io.aegisops.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

public class HttpMetricsFilter extends OncePerRequestFilter {
  private final MeterRegistry registry;
  private final ObservabilityProperties properties;

  public HttpMetricsFilter(MeterRegistry registry, ObservabilityProperties properties) {
    this.registry = registry;
    this.properties = properties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !properties.isEnabled()
        || !properties.isHttpMetricsEnabled()
        || request.getRequestURI().startsWith("/actuator");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long start = System.nanoTime();
    Throwable error = null;

    try {
      filterChain.doFilter(request, response);
    } catch (Throwable ex) {
      error = ex;
      throw ex;
    } finally {
      long duration = System.nanoTime() - start;

      String status = String.valueOf(response.getStatus());
      String method = request.getMethod();
      String uri = normalizedUri(request);
      String outcome = outcome(response.getStatus());
      String exception = error == null ? "none" : error.getClass().getSimpleName();

      Counter.builder("aegisops_http_requests_total")
          .description("AegisOps HTTP request count")
          .tag("service", properties.getServiceName())
          .tag("method", method)
          .tag("uri", uri)
          .tag("status", status)
          .tag("outcome", outcome)
          .tag("exception", exception)
          .register(registry)
          .increment();

      Timer.builder("aegisops_http_request_duration")
          .description("AegisOps HTTP request duration")
          .tag("service", properties.getServiceName())
          .tag("method", method)
          .tag("uri", uri)
          .tag("status", status)
          .tag("outcome", outcome)
          .tag("exception", exception)
          .publishPercentileHistogram()
          .register(registry)
          .record(duration, TimeUnit.NANOSECONDS);
    }
  }

  private String normalizedUri(HttpServletRequest request) {
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (pattern != null) {
      return String.valueOf(pattern);
    }

    String uri = request.getRequestURI();
    if (uri == null || uri.isBlank()) {
      return "UNKNOWN";
    }

    return uri.replaceAll("/[a-zA-Z0-9_-]{12,}", "/{id}");
  }

  private String outcome(int status) {
    if (status >= 500) {
      return "SERVER_ERROR";
    }
    if (status >= 400) {
      return "CLIENT_ERROR";
    }
    if (status >= 300) {
      return "REDIRECTION";
    }
    return "SUCCESS";
  }
}

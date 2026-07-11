package io.aegisops.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 为每个请求生成 requestId / traceId 并写入 MDC 与响应 Header。
 *
 * <p>客户端可以通过请求 Header 上送候选 requestId / traceId，但必须经过正则白名单清洗：
 *
 * <ul>
 *   <li>长度不超过 128 字符；
 *   <li>仅允许 {@code [A-Za-z0-9._:-]}；
 *   <li>不可包含换行或控制字符（避免日志注入 / MDC 污染 / 伪造其他请求 ID）。
 * </ul>
 *
 * <p>任何非法输入都会被丢弃并替换为新生成的 UUID，保证日志/响应中的 ID 永远安全。
 */
public class RequestIdFilter extends OncePerRequestFilter {

  private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

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

    String requestId = safeOrNew(request.getHeader(properties.getRequestIdHeader()));
    String traceId = safeOrFallback(request.getHeader(properties.getTraceIdHeader()), requestId);

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

  private String safeOrNew(String value) {
    String normalized = normalize(value);
    return normalized == null ? newId() : normalized;
  }

  private String safeOrFallback(String value, String fallback) {
    String normalized = normalize(value);
    return normalized == null ? fallback : normalized;
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }

    String trimmed = value.trim();

    if (trimmed.isEmpty()) {
      return null;
    }

    return SAFE_ID.matcher(trimmed).matches() ? trimmed : null;
  }

  private String newId() {
    return "req_" + UUID.randomUUID().toString().replace("-", "");
  }
}

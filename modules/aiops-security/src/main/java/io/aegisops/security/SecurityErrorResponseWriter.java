package io.aegisops.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 在 Spring Security Filter Chain 中以 JSON 形式输出错误响应。
 *
 * <p>所有响应都使用项目统一的 {@link ApiResponse} 契约：
 *
 * <pre>
 * {
 *   "success": false,
 *   "data": null,
 *   "errorCode": "...",
 *   "message": "...",
 *   "timestamp": "...",
 *   "requestId": "..."
 * }
 * </pre>
 *
 * <p>这一契约必须与 {@code GlobalExceptionHandler} 返回的 Controller 层错误结构保持一致， 避免前端不得不区分两套错误格式。
 */
public class SecurityErrorResponseWriter {

  private final ObjectMapper objectMapper;

  public SecurityErrorResponseWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void write(HttpServletResponse response, int status, String code, String message)
      throws IOException {
    write(response, status, code, message, null);
  }

  public void write(
      HttpServletResponse response, int status, String code, String message, Long retryAfterSeconds)
      throws IOException {
    if (response.isCommitted()) {
      return;
    }

    response.resetBuffer();
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    if (retryAfterSeconds != null) {
      response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, retryAfterSeconds)));
    }

    objectMapper.writeValue(response.getWriter(), ApiResponse.fail(code, message, requestId()));
  }

  private String requestId() {
    String value = MDC.get("requestId");
    return value == null || value.isBlank() ? null : value;
  }
}

package io.aegisops.common.api;

import java.time.OffsetDateTime;
import org.slf4j.MDC;

public record ApiResponse<T>(
    boolean success,
    T data,
    String errorCode,
    String message,
    OffsetDateTime timestamp,
    String requestId) {

  /**
   * 兼容原来的五参数构造函数。
   */
  public ApiResponse(
      boolean success,
      T data,
      String errorCode,
      String message,
      OffsetDateTime timestamp) {
    this(success, data, errorCode, message, timestamp, currentRequestId());
  }

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null, null, OffsetDateTime.now(), currentRequestId());
  }

  public static <T> ApiResponse<T> ok(T data, String requestId) {
    return new ApiResponse<>(true, data, null, null, OffsetDateTime.now(), requestId);
  }

  public static <T> ApiResponse<T> fail(String errorCode, String message) {
    return fail(errorCode, message, currentRequestId());
  }

  public static <T> ApiResponse<T> fail(String errorCode, String message, String requestId) {
    return new ApiResponse<>(false, null, errorCode, message, OffsetDateTime.now(), requestId);
  }

  private static String currentRequestId() {
    try {
      String value = MDC.get("requestId");
      if (value == null || value.isBlank()) {
        return null;
      }
      return value;
    } catch (RuntimeException ex) {
      // 静态访问 MDC 在没有 slf4j 绑定时不应当让业务报错。
      return null;
    }
  }
}

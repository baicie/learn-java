package io.aegisops.common.api;

import java.time.OffsetDateTime;

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
    this(success, data, errorCode, message, timestamp, null);
  }

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(true, data, null, null, OffsetDateTime.now(), null);
  }

  public static <T> ApiResponse<T> ok(T data, String requestId) {
    return new ApiResponse<>(true, data, null, null, OffsetDateTime.now(), requestId);
  }

  public static <T> ApiResponse<T> fail(String errorCode, String message) {
    return fail(errorCode, message, null);
  }

  public static <T> ApiResponse<T> fail(String errorCode, String message, String requestId) {
    return new ApiResponse<>(false, null, errorCode, message, OffsetDateTime.now(), requestId);
  }
}
package io.aegisops.common.exception;

public class AppException extends RuntimeException {

  private final String errorCode;
  private final int httpStatus;

  public AppException(ErrorCode errorCode, String message) {
    this(errorCode.name(), errorCode.httpStatus(), message, null);
  }

  public AppException(ErrorCode errorCode, String message, Throwable cause) {
    this(errorCode.name(), errorCode.httpStatus(), message, cause);
  }

  /**
   * 保留旧调用兼容性。旧字符串错误码默认按 400 处理；新代码必须优先使用 ErrorCode 枚举。
   */
  public AppException(String errorCode, String message) {
    this(errorCode, 400, message, null);
  }

  public AppException(String errorCode, int httpStatus, String message) {
    this(errorCode, httpStatus, message, null);
  }

  private AppException(String errorCode, int httpStatus, String message, Throwable cause) {
    super(message, cause);

    if (errorCode == null || errorCode.isBlank()) {
      throw new IllegalArgumentException("errorCode is required");
    }

    if (httpStatus < 400 || httpStatus > 599) {
      throw new IllegalArgumentException("httpStatus must be between 400 and 599");
    }

    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
  }

  public String errorCode() {
    return errorCode;
  }

  public int httpStatus() {
    return httpStatus;
  }
}
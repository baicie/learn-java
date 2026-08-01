package io.aegisops.common.security;

public class InvalidExecutionGrantException extends RuntimeException {
  public InvalidExecutionGrantException(String message) {
    super(message);
  }

  public InvalidExecutionGrantException(String message, Throwable cause) {
    super(message, cause);
  }
}

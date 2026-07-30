package io.aegisops.common.security;

public class InvalidDiagnosisGrantException extends RuntimeException {
  public InvalidDiagnosisGrantException(String message) {
    super(message);
  }

  public InvalidDiagnosisGrantException(String message, Throwable cause) {
    super(message, cause);
  }
}

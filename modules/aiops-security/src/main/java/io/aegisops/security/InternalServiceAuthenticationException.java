package io.aegisops.security;

public class InternalServiceAuthenticationException extends RuntimeException {
  public InternalServiceAuthenticationException(String message) {
    super(message);
  }

  public InternalServiceAuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }
}

package io.aegisops.security;

public class InternalServiceAuthenticationUnavailableException extends RuntimeException {
  public InternalServiceAuthenticationUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }

  public InternalServiceAuthenticationUnavailableException(String message) {
    super(message);
  }
}

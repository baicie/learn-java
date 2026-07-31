package io.aegisops.security;

public final class InternalServiceAuthorizationException
    extends InternalServiceAuthenticationException {
  public InternalServiceAuthorizationException(String message) {
    super(message);
  }
}

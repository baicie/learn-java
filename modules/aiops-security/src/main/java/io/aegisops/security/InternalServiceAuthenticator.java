package io.aegisops.security;

import jakarta.servlet.http.HttpServletRequest;

@FunctionalInterface
public interface InternalServiceAuthenticator {
  InternalServicePrincipal authenticate(HttpServletRequest request);
}

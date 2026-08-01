package io.aegisops.security;

import java.util.Set;

public record InternalServicePrincipal(String serviceId, Set<String> scopes) {
  public InternalServicePrincipal {
    scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
  }
}

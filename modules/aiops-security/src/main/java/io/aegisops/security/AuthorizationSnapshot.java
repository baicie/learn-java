package io.aegisops.security;

import java.util.Map;
import java.util.Set;

public record AuthorizationSnapshot(
    Set<String> roles, Set<String> permissions, Map<String, DataScope> dataScopes) {

  public AuthorizationSnapshot {
    roles = roles == null ? Set.of() : Set.copyOf(roles);

    permissions = permissions == null ? Set.of() : Set.copyOf(permissions);

    dataScopes = dataScopes == null ? Map.of() : Map.copyOf(dataScopes);
  }

  public boolean hasPermission(String permissionCode) {
    return permissions.contains(permissionCode);
  }

  public DataScope dataScope(String resourceCode) {
    return dataScopes.getOrDefault(resourceCode, DataScope.SELF);
  }
}

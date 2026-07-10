package io.aegisops.security.api;

import io.aegisops.security.DataScope;
import io.aegisops.security.UserPrincipal;
import java.util.Map;
import java.util.Set;

public record CurrentAuthorizationResponse(
    String userId,
    String tenantId,
    String username,
    String displayName,
    Set<String> roles,
    Set<String> permissions,
    Map<String, DataScope> dataScopes) {

  public static CurrentAuthorizationResponse from(UserPrincipal principal) {
    return new CurrentAuthorizationResponse(
        principal.id(),
        principal.tenantId(),
        principal.getUsername(),
        principal.displayName(),
        principal.roles(),
        principal.permissions(),
        principal.dataScopes());
  }
}

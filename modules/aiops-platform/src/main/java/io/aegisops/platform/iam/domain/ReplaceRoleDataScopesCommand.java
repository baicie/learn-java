package io.aegisops.platform.iam.domain;

import java.util.Collection;
import java.util.List;

public record ReplaceRoleDataScopesCommand(
    Collection<PlatformRole.RoleDataScope> scopes, int rowVersion) {

  public List<PlatformRole.RoleDataScope> normalized() {
    if (scopes == null) {
      return List.of();
    }
    return scopes.stream()
        .filter(s -> s != null && s.resourceCode() != null && !s.resourceCode().isBlank())
        .map(
            scope ->
                new PlatformRole.RoleDataScope(
                    scope.resourceCode(),
                    scope.scopeType() == null ? "SELF" : scope.scopeType(),
                    scope.detail() == null ? java.util.Map.of() : scope.detail()))
        .toList();
  }
}
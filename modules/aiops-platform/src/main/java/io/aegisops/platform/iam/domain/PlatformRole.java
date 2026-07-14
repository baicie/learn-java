package io.aegisops.platform.iam.domain;

import java.util.Map;
import java.util.Set;

public record PlatformRole(
    String code,
    String name,
    String description,
    boolean system,
    boolean enabled,
    Set<String> permissions,
    Map<String, RoleDataScope> dataScopes,
    int rowVersion) {

  public record RoleDataScope(String resourceCode, String scopeType, Map<String, Object> detail) {}
}

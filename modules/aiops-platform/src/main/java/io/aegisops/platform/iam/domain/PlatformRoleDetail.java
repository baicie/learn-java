package io.aegisops.platform.iam.domain;

import java.util.Map;
import java.util.Set;

public record PlatformRoleDetail(
    String roleCode,
    String roleName,
    String description,
    boolean system,
    boolean enabled,
    Set<String> permissions,
    Map<String, RoleDataScope> dataScopes,
    int userCount,
    int rowVersion) {

  public record RoleDataScope(String resourceCode, String scopeType, Map<String, Object> detail) {}
}

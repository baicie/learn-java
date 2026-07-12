package io.aegisops.platform.iam.domain;

import java.util.Set;

public record UpdateRoleData(
    String name,
    String description,
    Boolean enabled,
    Set<String> permissionCodes) {}
package io.aegisops.platform.iam.domain;

import java.util.Set;

public record PermissionDefinition(
    String code,
    String moduleCode,
    String name,
    String description,
    PermissionRisk risk,
    Set<String> dependencies,
    int sortOrder,
    boolean enabled) {}
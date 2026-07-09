package io.aegisops.platform.bootstrap;

public record PlatformPermissionCode(
    String id,
    String permissionCode,
    String permissionName,
    String moduleCode,
    String description,
    int sortOrder) {}

package io.aegisops.platform;

import java.time.OffsetDateTime;

public record PlatformMenuItem(
    String id,
    String moduleId,
    String parentId,
    String path,
    String title,
    String icon,
    String permissionCode,
    int sortOrder,
    boolean enabled,
    OffsetDateTime createdAt) {}

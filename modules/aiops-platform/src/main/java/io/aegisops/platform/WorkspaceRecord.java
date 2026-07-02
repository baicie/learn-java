package io.aegisops.platform;

import java.time.OffsetDateTime;

public record WorkspaceRecord(
    String id,
    String tenantId,
    String code,
    String name,
    boolean enabled,
    OffsetDateTime createdAt) {}

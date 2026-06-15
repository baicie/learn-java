package io.aegisops.tenant;

import java.time.OffsetDateTime;

public record Tenant(
    String id,
    String code,
    String name,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

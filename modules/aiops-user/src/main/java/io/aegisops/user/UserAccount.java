package io.aegisops.user;

import java.time.OffsetDateTime;

public record UserAccount(
    String id,
    String tenantId,
    String username,
    String displayName,
    String email,
    String passwordHash,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

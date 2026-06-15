package io.aegisops.user;

import java.time.OffsetDateTime;
import java.util.Set;

public record UserAccount(
    String id,
    String tenantId,
    String username,
    String displayName,
    String email,
    String passwordHash,
    String status,
    Set<String> roles,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

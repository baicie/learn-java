package io.aegisops.platform.iam.repository;

import io.aegisops.platform.iam.domain.PlatformUserStatus;
import java.time.OffsetDateTime;

public record PlatformUserCreateCommand(
    String id,
    String tenantId,
    String username,
    String displayName,
    String email,
    String passwordHash,
    PlatformUserStatus status,
    OffsetDateTime now) {}

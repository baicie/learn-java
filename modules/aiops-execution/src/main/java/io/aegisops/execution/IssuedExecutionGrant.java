package io.aegisops.execution;

import java.time.OffsetDateTime;

public record IssuedExecutionGrant(String token, String snapshotSha256, OffsetDateTime expiresAt) {}

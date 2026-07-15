package io.aegisops.workrecord.domain.model;

import java.time.OffsetDateTime;

public record UploadSession(
    String id,
    String tenantId,
    String requestedBy,
    String purpose,
    String objectKey,
    String originalFileName,
    String contentType,
    long declaredSizeBytes,
    String status,
    OffsetDateTime expiresAt,
    OffsetDateTime consumedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

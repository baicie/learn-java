package io.aegisops.workrecord.domain.model;

import java.time.OffsetDateTime;

public record AsyncJob(
    String id,
    String tenantId,
    AsyncJobType jobType,
    AsyncJobStatus status,
    String requestedBy,
    String requestJson,
    String resultJson,
    String sourceObjectKey,
    int totalCount,
    int processedCount,
    int successCount,
    int failureCount,
    String resultObjectKey,
    String resultFileName,
    String resultContentType,
    String errorObjectKey,
    String idempotencyKey,
    String errorMessage,
    long rowVersion,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime expiresAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

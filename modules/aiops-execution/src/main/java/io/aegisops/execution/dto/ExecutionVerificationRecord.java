package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionVerificationRecord(
    String id,
    String tenantId,
    String executionId,
    String stepId,
    String verificationType,
    String targetType,
    String targetId,
    String status,
    String summary,
    String detailsJson,
    String createdBy,
    OffsetDateTime createdAt) {}

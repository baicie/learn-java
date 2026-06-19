package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionVerificationResponse(
    String id,
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

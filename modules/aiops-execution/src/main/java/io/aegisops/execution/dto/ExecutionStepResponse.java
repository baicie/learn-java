package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionStepResponse(
    String id,
    String executionId,
    String planStepId,
    int sequenceNo,
    String name,
    String actionType,
    String targetType,
    String status,
    String output,
    String errorMessage,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

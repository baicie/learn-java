package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionStepRecord(
    String id,
    String tenantId,
    String executionId,
    String planStepId,
    int sequenceNo,
    String name,
    String actionType,
    String targetType,
    String status,
    String actionPayloadJson,
    String commandSnapshot,
    String output,
    String errorMessage,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    int attempt,
    int timeoutSeconds,
    int artifactCount,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

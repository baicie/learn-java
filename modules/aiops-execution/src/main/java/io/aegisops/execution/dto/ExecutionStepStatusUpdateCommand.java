package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionStepStatusUpdateCommand(
    String tenantId,
    String stepId,
    String status,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    String output,
    String errorMessage) {}

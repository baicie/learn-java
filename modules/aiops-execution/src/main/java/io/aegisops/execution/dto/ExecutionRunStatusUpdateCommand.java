package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionRunStatusUpdateCommand(
    String tenantId,
    String executionId,
    String status,
    String runnerId,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    String errorMessage,
    String summary) {}

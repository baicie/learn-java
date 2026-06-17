package io.aegisops.execution.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ExecutionRunResponse(
    String id,
    String tenantId,
    String incidentId,
    String planId,
    String status,
    String mode,
    String requestedBy,
    String runnerId,
    String errorMessage,
    String summary,
    List<ExecutionStepResponse> steps,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

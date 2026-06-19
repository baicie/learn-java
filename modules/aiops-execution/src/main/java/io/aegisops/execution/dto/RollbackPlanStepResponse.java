package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record RollbackPlanStepResponse(
    String id,
    String sourceStepId,
    int stepOrder,
    String title,
    String description,
    String actionType,
    String targetType,
    String actionPayloadJson,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

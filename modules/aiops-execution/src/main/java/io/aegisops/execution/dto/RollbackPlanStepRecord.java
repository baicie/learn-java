package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record RollbackPlanStepRecord(
    String id,
    String tenantId,
    String rollbackPlanId,
    String sourceStepId,
    int stepOrder,
    String title,
    String description,
    String actionType,
    String targetType,
    String actionPayloadJson,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

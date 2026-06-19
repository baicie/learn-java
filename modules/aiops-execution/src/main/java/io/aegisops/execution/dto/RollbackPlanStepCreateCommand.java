package io.aegisops.execution.dto;

public record RollbackPlanStepCreateCommand(
    String id,
    String tenantId,
    String rollbackPlanId,
    String sourceStepId,
    int stepOrder,
    String title,
    String description,
    String actionType,
    String targetType,
    String actionPayloadJson) {}

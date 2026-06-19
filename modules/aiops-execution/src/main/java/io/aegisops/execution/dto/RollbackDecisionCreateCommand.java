package io.aegisops.execution.dto;

public record RollbackDecisionCreateCommand(
    String id,
    String tenantId,
    String rollbackPlanId,
    String reviewer,
    String decision,
    String comment) {}

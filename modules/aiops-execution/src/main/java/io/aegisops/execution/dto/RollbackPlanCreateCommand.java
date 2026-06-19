package io.aegisops.execution.dto;

public record RollbackPlanCreateCommand(
    String id,
    String tenantId,
    String incidentId,
    String sourcePlanId,
    String sourceExecutionId,
    String status,
    String riskLevel,
    String reason,
    int requiredApprovals,
    String createdBy) {}

package io.aegisops.execution.dto;

public record RollbackPlanCreateRequest(
    String reason, String riskLevel, Integer requiredApprovals, String createdBy) {}

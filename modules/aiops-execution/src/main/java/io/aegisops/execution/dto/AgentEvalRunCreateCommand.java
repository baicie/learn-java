package io.aegisops.execution.dto;

public record AgentEvalRunCreateCommand(
    String id,
    String tenantId,
    String datasetId,
    String promptProfileId,
    String mode,
    String status,
    int totalCases,
    int passedCases,
    int failedCases,
    double averageScore,
    String summary,
    String createdBy) {}

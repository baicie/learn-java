package io.aegisops.execution.dto;

public record AgentEvalRunFinishCommand(
    String tenantId,
    String runId,
    String status,
    int totalCases,
    int passedCases,
    int failedCases,
    double averageScore,
    String summary) {}

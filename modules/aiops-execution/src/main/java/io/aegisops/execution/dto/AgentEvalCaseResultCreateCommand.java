package io.aegisops.execution.dto;

public record AgentEvalCaseResultCreateCommand(
    String id,
    String tenantId,
    String runId,
    String caseId,
    String actualDiagnosisId,
    String actualSummary,
    String actualRootCause,
    String actualRecommendation,
    double score,
    double rootCauseScore,
    double keywordScore,
    double actionScore,
    double safetyScore,
    boolean passed,
    String detailsJson) {}

package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AgentEvalCaseResultRecord(
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
    String detailsJson,
    OffsetDateTime createdAt) {}

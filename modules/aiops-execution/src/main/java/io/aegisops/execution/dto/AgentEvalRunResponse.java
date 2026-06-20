package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AgentEvalRunResponse(
    String id,
    String datasetId,
    String promptProfileId,
    String mode,
    String status,
    int totalCases,
    int passedCases,
    int failedCases,
    double averageScore,
    String summary,
    String createdBy,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

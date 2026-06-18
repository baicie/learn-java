package io.aegisops.execution.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ExecutionRunResponse(
    String id,
    String tenantId,
    String incidentId,
    String planId,
    String status,
    String mode,
    String requestedBy,
    String runnerId,
    String errorMessage,
    String summary,
    int attempt,
    int maxAttempts,
    String retryOfExecutionId,
    OffsetDateTime leaseUntil,
    OffsetDateTime heartbeatAt,
    int timeoutSeconds,
    String approvalId,
    String approvalSnapshotJson,
    String planRiskLevel,
    OffsetDateTime liveGuardPassedAt,
    List<ExecutionStepResponse> steps,
    List<ExecutionArtifactResponse> artifacts,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

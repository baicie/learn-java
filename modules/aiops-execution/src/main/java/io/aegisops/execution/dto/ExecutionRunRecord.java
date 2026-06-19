package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionRunRecord(
    String id,
    String tenantId,
    String incidentId,
    String planId,
    String status,
    String mode,
    String requestedBy,
    String runnerId,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
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
    String executionKind,
    String rollbackPlanId,
    String rollbackOfExecutionId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

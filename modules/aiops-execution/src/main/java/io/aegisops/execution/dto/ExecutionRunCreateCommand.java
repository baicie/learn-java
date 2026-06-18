package io.aegisops.execution.dto;

public record ExecutionRunCreateCommand(
    String id,
    String tenantId,
    String incidentId,
    String planId,
    String status,
    String mode,
    String requestedBy,
    int attempt,
    int maxAttempts,
    String retryOfExecutionId,
    int timeoutSeconds,
    String approvalId,
    String approvalSnapshotJson,
    String planRiskLevel) {}

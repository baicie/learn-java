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
    String planRiskLevel,
    String executionKind,
    String rollbackPlanId,
    String rollbackOfExecutionId,
    String executionGrant,
    String executionSnapshotSha256,
    java.time.OffsetDateTime executionGrantExpiresAt) {
  public ExecutionRunCreateCommand withGrant(
      String token, String snapshotSha256, java.time.OffsetDateTime expiresAt) {
    return new ExecutionRunCreateCommand(
        id,
        tenantId,
        incidentId,
        planId,
        status,
        mode,
        requestedBy,
        attempt,
        maxAttempts,
        retryOfExecutionId,
        timeoutSeconds,
        approvalId,
        approvalSnapshotJson,
        planRiskLevel,
        executionKind,
        rollbackPlanId,
        rollbackOfExecutionId,
        token,
        snapshotSha256,
        expiresAt);
  }
}

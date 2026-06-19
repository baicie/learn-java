package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record RollbackPlanRecord(
    String id,
    String tenantId,
    String incidentId,
    String sourcePlanId,
    String sourceExecutionId,
    String status,
    String riskLevel,
    String reason,
    int requiredApprovals,
    int approvedCount,
    int rejectedCount,
    String createdBy,
    String submittedBy,
    OffsetDateTime submittedAt,
    OffsetDateTime decidedAt,
    String approvalSnapshotJson,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

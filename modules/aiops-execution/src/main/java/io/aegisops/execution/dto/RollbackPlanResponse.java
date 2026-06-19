package io.aegisops.execution.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record RollbackPlanResponse(
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
    List<RollbackPlanStepResponse> steps,
    List<RollbackDecisionResponse> decisions,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

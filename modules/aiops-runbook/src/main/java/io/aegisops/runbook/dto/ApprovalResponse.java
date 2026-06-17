package io.aegisops.runbook.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ApprovalResponse(
    String id,
    String tenantId,
    String incidentId,
    String planId,
    String status,
    String riskLevel,
    int requiredApprovals,
    int approvedCount,
    int rejectedCount,
    String submittedBy,
    OffsetDateTime submittedAt,
    OffsetDateTime completedAt,
    String reason,
    List<ApprovalDecisionResponse> decisions,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

package io.aegisops.runbook.dto;

import java.time.OffsetDateTime;

public record AutomationApprovalCreateCommand(
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
    String reason) {}

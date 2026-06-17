package io.aegisops.runbook.dto;

import java.time.OffsetDateTime;

public record ApprovalProgressUpdateCommand(
    String tenantId,
    String approvalId,
    String status,
    int approvedCount,
    int rejectedCount,
    OffsetDateTime completedAt) {}

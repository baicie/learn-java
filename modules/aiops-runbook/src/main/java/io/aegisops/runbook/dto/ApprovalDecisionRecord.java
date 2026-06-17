package io.aegisops.runbook.dto;

import java.time.OffsetDateTime;

public record ApprovalDecisionRecord(
    String id,
    String tenantId,
    String approvalId,
    String planId,
    String reviewer,
    String decision,
    String comment,
    OffsetDateTime decidedAt,
    OffsetDateTime createdAt) {}

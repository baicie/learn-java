package io.aegisops.runbook.dto;

import java.time.OffsetDateTime;

public record ApprovalPolicyRecord(
    String id,
    String tenantId,
    String riskLevel,
    int requiredApprovals,
    boolean requireComment,
    boolean enabled,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

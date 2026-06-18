package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionApprovalSnapshotRecord(
    String approvalId,
    String planId,
    String status,
    String submittedBy,
    int requiredApprovals,
    int approvedCount,
    OffsetDateTime approvedAt) {}

package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record RollbackDecisionRecord(
    String id,
    String tenantId,
    String rollbackPlanId,
    String reviewer,
    String decision,
    String comment,
    OffsetDateTime createdAt) {}

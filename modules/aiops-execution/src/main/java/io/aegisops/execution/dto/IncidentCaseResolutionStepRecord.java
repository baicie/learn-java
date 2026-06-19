package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record IncidentCaseResolutionStepRecord(
    String id,
    String tenantId,
    String caseId,
    int stepOrder,
    String title,
    String description,
    String actionType,
    String sourceRefId,
    OffsetDateTime createdAt) {}

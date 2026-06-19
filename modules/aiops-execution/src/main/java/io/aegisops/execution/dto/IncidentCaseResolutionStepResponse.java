package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record IncidentCaseResolutionStepResponse(
    String id,
    int stepOrder,
    String title,
    String description,
    String actionType,
    String sourceRefId,
    OffsetDateTime createdAt) {}

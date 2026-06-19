package io.aegisops.execution.dto;

public record IncidentCaseResolutionStepCreateCommand(
    String id,
    String tenantId,
    String caseId,
    int stepOrder,
    String title,
    String description,
    String actionType,
    String sourceRefId) {}

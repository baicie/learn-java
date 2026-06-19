package io.aegisops.execution.dto;

public record IncidentCaseCreateCommand(
    String id,
    String tenantId,
    String sourcePostmortemId,
    String incidentId,
    String status,
    String severity,
    String title,
    String summary,
    String rootCause,
    String resolution,
    String prevention,
    int qualityScore,
    String createdBy) {}

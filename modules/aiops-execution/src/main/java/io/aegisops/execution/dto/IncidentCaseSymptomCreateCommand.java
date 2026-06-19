package io.aegisops.execution.dto;

public record IncidentCaseSymptomCreateCommand(
    String id,
    String tenantId,
    String caseId,
    String symptomType,
    String name,
    String description) {}

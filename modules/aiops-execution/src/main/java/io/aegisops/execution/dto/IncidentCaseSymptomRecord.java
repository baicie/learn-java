package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record IncidentCaseSymptomRecord(
    String id,
    String tenantId,
    String caseId,
    String symptomType,
    String name,
    String description,
    OffsetDateTime createdAt) {}

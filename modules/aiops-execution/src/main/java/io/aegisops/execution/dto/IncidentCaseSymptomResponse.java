package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record IncidentCaseSymptomResponse(
    String id, String symptomType, String name, String description, OffsetDateTime createdAt) {}

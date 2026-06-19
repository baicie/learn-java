package io.aegisops.execution.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record IncidentCaseResponse(
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
    String createdBy,
    String reviewedBy,
    OffsetDateTime publishedAt,
    OffsetDateTime archivedAt,
    List<IncidentCaseSymptomResponse> symptoms,
    List<IncidentCaseResolutionStepResponse> resolutionSteps,
    List<String> tags,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

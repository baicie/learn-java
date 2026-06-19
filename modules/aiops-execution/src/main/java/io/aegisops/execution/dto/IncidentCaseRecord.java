package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record IncidentCaseRecord(
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
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record PostmortemReportRecord(
    String id,
    String tenantId,
    String incidentId,
    String status,
    String severity,
    String title,
    String summary,
    String impact,
    String rootCause,
    String detection,
    String resolution,
    String prevention,
    String markdown,
    String sourceSnapshotJson,
    String generatedBy,
    OffsetDateTime generatedAt,
    String reviewedBy,
    OffsetDateTime reviewedAt,
    OffsetDateTime archivedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

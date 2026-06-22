package io.aegisops.report;

import java.time.OffsetDateTime;

public record IncidentReportRecord(
    String id,
    String tenantId,
    String incidentId,
    int versionNo,
    String reportType,
    String format,
    String title,
    String markdownContent,
    String snapshotJson,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

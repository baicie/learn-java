package io.aegisops.report;

import java.time.OffsetDateTime;

public record IncidentReportResponse(
    String id,
    String incidentId,
    int versionNo,
    String reportType,
    String format,
    String title,
    String markdownContent,
    String snapshotJson,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {
  public static IncidentReportResponse from(IncidentReportRecord record) {
    return new IncidentReportResponse(
        record.id(),
        record.incidentId(),
        record.versionNo(),
        record.reportType(),
        record.format(),
        record.title(),
        record.markdownContent(),
        record.snapshotJson(),
        record.createdBy(),
        record.createdAt(),
        record.updatedAt());
  }
}

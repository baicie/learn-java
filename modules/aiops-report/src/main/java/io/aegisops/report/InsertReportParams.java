package io.aegisops.report;

public record InsertReportParams(
    String id,
    String tenantId,
    String incidentId,
    int versionNo,
    String title,
    String markdownContent,
    String snapshotJson,
    String createdBy) {}

package io.aegisops.execution.dto;

public record PostmortemReportCreateCommand(
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
    String generatedBy) {}

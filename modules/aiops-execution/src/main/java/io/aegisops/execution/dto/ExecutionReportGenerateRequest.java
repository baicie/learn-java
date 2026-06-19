package io.aegisops.execution.dto;

public record ExecutionReportGenerateRequest(
    String reportType,
    String generatedBy,
    Boolean includeArtifacts,
    Boolean includeVerifications,
    Boolean includeAuditEvents) {}

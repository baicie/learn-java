package io.aegisops.execution.dto;

public record ExecutionReportCreateCommand(
    String id,
    String tenantId,
    String executionId,
    String reportType,
    String status,
    String title,
    String summary,
    String markdown,
    String generatedBy) {}

package io.aegisops.execution.dto;

public record ExecutionReportSectionCreateCommand(
    String id,
    String tenantId,
    String reportId,
    int sectionOrder,
    String sectionType,
    String title,
    String content,
    String metadataJson) {}

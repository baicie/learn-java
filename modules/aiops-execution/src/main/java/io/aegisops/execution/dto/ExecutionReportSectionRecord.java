package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionReportSectionRecord(
    String id,
    String tenantId,
    String reportId,
    int sectionOrder,
    String sectionType,
    String title,
    String content,
    String metadataJson,
    OffsetDateTime createdAt) {}

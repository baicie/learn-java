package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionReportRecord(
    String id,
    String tenantId,
    String executionId,
    String reportType,
    String status,
    String title,
    String summary,
    String markdown,
    String generatedBy,
    OffsetDateTime generatedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

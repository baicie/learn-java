package io.aegisops.execution.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ExecutionReportResponse(
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
    List<ExecutionReportSectionResponse> sections,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

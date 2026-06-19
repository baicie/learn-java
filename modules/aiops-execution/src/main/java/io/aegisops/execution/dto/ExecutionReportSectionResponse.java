package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionReportSectionResponse(
    String id,
    int sectionOrder,
    String sectionType,
    String title,
    String content,
    String metadataJson,
    OffsetDateTime createdAt) {}

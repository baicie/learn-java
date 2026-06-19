package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record PostmortemSectionResponse(
    String id,
    int sectionOrder,
    String sectionType,
    String title,
    String content,
    String metadataJson,
    OffsetDateTime createdAt) {}

package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record PostmortemSectionRecord(
    String id,
    String tenantId,
    String postmortemId,
    int sectionOrder,
    String sectionType,
    String title,
    String content,
    String metadataJson,
    OffsetDateTime createdAt) {}

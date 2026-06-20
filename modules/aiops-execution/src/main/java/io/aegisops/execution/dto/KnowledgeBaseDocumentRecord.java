package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record KnowledgeBaseDocumentRecord(
    String id,
    String tenantId,
    String sourceType,
    String sourceId,
    String title,
    String status,
    String metadataJson,
    OffsetDateTime indexedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

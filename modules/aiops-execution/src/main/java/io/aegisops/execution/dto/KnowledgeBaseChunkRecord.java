package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record KnowledgeBaseChunkRecord(
    String id,
    String tenantId,
    String documentId,
    int chunkOrder,
    String sourceType,
    String sourceId,
    String title,
    String content,
    String contentHash,
    int tokenEstimate,
    String embeddingJson,
    String metadataJson,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

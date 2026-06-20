package io.aegisops.execution.dto;

public record KnowledgeBaseChunkCreateCommand(
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
    String status) {}

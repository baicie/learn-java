package io.aegisops.execution.dto;

public record KnowledgeBaseIndexResponse(
    String documentId, String sourceType, String sourceId, int chunkCount, String status) {}

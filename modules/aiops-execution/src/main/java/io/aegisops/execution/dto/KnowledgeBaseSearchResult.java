package io.aegisops.execution.dto;

public record KnowledgeBaseSearchResult(
    String documentId,
    String chunkId,
    String sourceType,
    String sourceId,
    String title,
    String content,
    double score,
    double vectorScore,
    double keywordScore,
    String metadataJson) {}

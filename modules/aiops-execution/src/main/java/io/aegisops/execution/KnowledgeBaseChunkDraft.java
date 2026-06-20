package io.aegisops.execution;

public record KnowledgeBaseChunkDraft(
    int chunkOrder, String title, String content, String metadataJson) {}

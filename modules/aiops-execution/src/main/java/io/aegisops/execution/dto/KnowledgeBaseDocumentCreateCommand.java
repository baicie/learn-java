package io.aegisops.execution.dto;

public record KnowledgeBaseDocumentCreateCommand(
    String id,
    String tenantId,
    String sourceType,
    String sourceId,
    String title,
    String status,
    String metadataJson) {}

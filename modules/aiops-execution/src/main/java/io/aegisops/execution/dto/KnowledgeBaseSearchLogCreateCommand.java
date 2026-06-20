package io.aegisops.execution.dto;

public record KnowledgeBaseSearchLogCreateCommand(
    String id,
    String tenantId,
    String query,
    String sourceTypesJson,
    String tagsJson,
    int topK,
    int resultCount,
    String createdBy) {}

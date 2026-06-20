package io.aegisops.execution.dto;

public record AgentMemoryEventCreateCommand(
    String id,
    String tenantId,
    String memoryId,
    String eventType,
    String summary,
    String actor,
    String metadataJson) {}

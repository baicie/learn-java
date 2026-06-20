package io.aegisops.execution.dto;

import java.util.List;

public record AgentMemoryCreateRequest(
    String tenantId,
    String scopeType,
    String scopeId,
    String memoryType,
    String sourceType,
    String sourceId,
    String title,
    String content,
    List<String> tags,
    Double confidence,
    Integer ttlSeconds,
    String createdBy) {}

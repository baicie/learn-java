package io.aegisops.execution.dto;

import java.util.List;

public record AgentMemorySearchResult(
    String id,
    String scopeType,
    String scopeId,
    String memoryType,
    String sourceType,
    String sourceId,
    String title,
    String content,
    List<String> tags,
    double confidence,
    double score,
    String createdBy) {}

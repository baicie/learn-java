package io.aegisops.execution.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AgentMemoryResponse(
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
    String status,
    String createdBy,
    OffsetDateTime expiresAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AgentMemoryCreateCommand(
    String id,
    String tenantId,
    String scopeType,
    String scopeId,
    String memoryType,
    String sourceType,
    String sourceId,
    String title,
    String content,
    String tagsJson,
    double confidence,
    String status,
    String createdBy,
    OffsetDateTime expiresAt) {}

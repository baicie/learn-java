package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AgentEvalDatasetRecord(
    String id,
    String tenantId,
    String name,
    String description,
    String status,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AgentEvalDatasetResponse(
    String id,
    String name,
    String description,
    String status,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

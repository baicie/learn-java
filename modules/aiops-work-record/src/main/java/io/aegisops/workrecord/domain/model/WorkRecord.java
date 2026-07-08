package io.aegisops.workrecord.domain.model;

import java.time.OffsetDateTime;

public record WorkRecord(
    String id,
    String tenantId,
    String templateId,
    String title,
    String status,
    String ownerId,
    String creatorId,
    OffsetDateTime recordTime,
    String builtinDataJson,
    String customDataJson,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

package io.aegisops.workrecord.domain.model;

import java.time.OffsetDateTime;

public record WorkRecord(
    String id,
    String tenantId,
    String templateId,
    String templateVersionId,
    String title,
    RecordStatus status,
    String ownerId,
    String creatorId,
    OffsetDateTime recordTime,
    String builtinDataJson,
    String customDataJson,
    int rowVersion,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime deletedAt) {}

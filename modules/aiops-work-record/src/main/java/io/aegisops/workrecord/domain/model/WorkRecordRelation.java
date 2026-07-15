package io.aegisops.workrecord.domain.model;

import java.time.OffsetDateTime;

public record WorkRecordRelation(
    String id,
    String recordId,
    RelationType relationType,
    String targetId,
    String targetTitle,
    String targetStatus,
    String snapshotJson,
    String createdBy,
    OffsetDateTime createdAt) {}

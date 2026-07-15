package io.aegisops.workrecord.domain.model;

import java.time.OffsetDateTime;
import java.util.List;

public record WorkRecordComment(
    String id,
    String recordId,
    String content,
    List<String> mentionUserIds,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    int rowVersion) {}

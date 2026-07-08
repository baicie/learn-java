package io.aegisops.workrecord.api.dto;

import java.time.OffsetDateTime;

public record CreateWorkRecordRequest(
    String templateId,
    String title,
    String status,
    String ownerId,
    OffsetDateTime recordTime,
    String builtinDataJson,
    String customDataJson) {}

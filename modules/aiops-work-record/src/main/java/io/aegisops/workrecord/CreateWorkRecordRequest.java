package io.aegisops.workrecord;

import java.time.OffsetDateTime;

public record CreateWorkRecordRequest(
    String templateId,
    String title,
    String status,
    String ownerId,
    OffsetDateTime recordTime,
    String builtinDataJson,
    String customDataJson) {}

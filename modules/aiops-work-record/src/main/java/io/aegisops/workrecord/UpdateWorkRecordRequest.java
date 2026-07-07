package io.aegisops.workrecord;

import java.time.OffsetDateTime;

/** 更新工作记录请求；所有字段可选，仅非 null 时生效。 */
public record UpdateWorkRecordRequest(
    String title,
    String status,
    String ownerId,
    OffsetDateTime recordTime,
    String builtinDataJson,
    String customDataJson) {}

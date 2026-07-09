package io.aegisops.workrecord.application.command;

import java.time.OffsetDateTime;

public record CreateRecordCommand(
    String templateId,
    String templateVersionId,
    String title,
    String status,
    String ownerId,
    OffsetDateTime recordTime,
    String builtinDataJson,
    String customDataJson) {}

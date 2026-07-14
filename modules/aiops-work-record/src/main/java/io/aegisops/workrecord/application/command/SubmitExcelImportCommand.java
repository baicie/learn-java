package io.aegisops.workrecord.application.command;

import java.time.OffsetDateTime;

public record SubmitExcelImportCommand(
    String uploadId,
    String templateId,
    String templateVersionId,
    String defaultStatus,
    String defaultOwnerId,
    OffsetDateTime defaultRecordTime,
    boolean stopOnError) {}

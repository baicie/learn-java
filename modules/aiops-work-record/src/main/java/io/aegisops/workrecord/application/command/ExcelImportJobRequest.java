package io.aegisops.workrecord.application.command;

import java.time.OffsetDateTime;

public record ExcelImportJobRequest(
    String templateId,
    String templateVersionId,
    String sourceObjectKey,
    String originalFileName,
    String contentType,
    long declaredSizeBytes,
    String defaultStatus,
    String defaultOwnerId,
    OffsetDateTime defaultRecordTime,
    boolean stopOnError) {}

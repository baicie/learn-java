package io.aegisops.workrecord.domain.model;

import java.time.OffsetDateTime;

public record WorkRecordAttachment(
    String id,
    String recordId,
    String uploadId,
    String objectKey,
    String fileName,
    String contentType,
    long sizeBytes,
    String sha256,
    String status,
    String uploadedBy,
    OffsetDateTime createdAt) {}

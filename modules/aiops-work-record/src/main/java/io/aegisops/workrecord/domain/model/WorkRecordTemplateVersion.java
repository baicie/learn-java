package io.aegisops.workrecord.domain.model;

import java.time.OffsetDateTime;

public record WorkRecordTemplateVersion(
    String id,
    String tenantId,
    String templateId,
    int versionNo,
    String versionName,
    String schemaJson,
    String designerJson,
    String fieldIndexJson,
    String publishedBy,
    OffsetDateTime publishedAt,
    OffsetDateTime createdAt) {}

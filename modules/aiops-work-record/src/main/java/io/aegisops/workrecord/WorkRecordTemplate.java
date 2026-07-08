package io.aegisops.workrecord;

import java.time.OffsetDateTime;

public record WorkRecordTemplate(
    String id,
    String tenantId,
    String name,
    String code,
    String description,
    boolean enabled,
    String schemaJson,
    String designerJson,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

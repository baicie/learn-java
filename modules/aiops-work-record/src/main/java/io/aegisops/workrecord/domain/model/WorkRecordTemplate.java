package io.aegisops.workrecord.domain.model;

import java.time.OffsetDateTime;

public record WorkRecordTemplate(
    String id,
    String tenantId,
    String code,
    String name,
    String description,
    TemplateStatus status,
    boolean enabled,
    String currentVersionId,
    String draftSchemaJson,
    String draftDesignerJson,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime deletedAt) {}

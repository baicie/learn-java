package io.aegisops.workrecord.domain.model;

import java.time.OffsetDateTime;

public record WorkRecordField(
    String id,
    String tenantId,
    String templateId,
    String fieldName,
    String fieldCode,
    String fieldType,
    boolean required,
    String defaultValue,
    String optionSource,
    String dictCode,
    String optionsJson,
    boolean listVisible,
    boolean filterable,
    boolean exportable,
    boolean statistical,
    int sortOrder,
    boolean enabled,
    String schemaPath,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

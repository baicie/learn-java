package io.aegisops.workrecord;

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
    boolean statistical,
    int sortOrder,
    boolean enabled,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

package io.aegisops.platform.dictionary;

import java.time.OffsetDateTime;

public record DictItemRecord(
    String id,
    String tenantId,
    String dictTypeId,
    String itemLabel,
    String itemValue,
    String color,
    String icon,
    String description,
    boolean systemBuiltin,
    boolean enabled,
    int sortOrder,
    String extraJson,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

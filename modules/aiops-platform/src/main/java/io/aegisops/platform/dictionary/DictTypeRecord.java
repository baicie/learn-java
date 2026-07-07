package io.aegisops.platform.dictionary;

import java.time.OffsetDateTime;

public record DictTypeRecord(
    String id,
    String tenantId,
    String dictCode,
    String dictName,
    String description,
    boolean systemBuiltin,
    boolean enabled,
    int sortOrder,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

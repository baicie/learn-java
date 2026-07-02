package io.aegisops.inspection;

import java.time.OffsetDateTime;

public record InspectionTaskRecord(
    String id,
    String tenantId,
    String name,
    String targetType,
    String targetQueryJson,
    String templateKey,
    boolean enabled,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

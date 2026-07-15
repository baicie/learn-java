package io.aegisops.workrecord.domain.model;

import java.time.OffsetDateTime;

public record WorkRecordNotification(
    String id,
    String tenantId,
    String userId,
    String notificationType,
    String title,
    String content,
    String resourceType,
    String resourceId,
    String dedupeKey,
    OffsetDateTime createdAt,
    OffsetDateTime readAt) {}

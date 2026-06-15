package io.aegisops.audit;

import java.time.OffsetDateTime;

public record AuditLog(
    String id,
    String tenantId,
    String actorUserId,
    String action,
    String targetType,
    String targetId,
    String detailJson,
    OffsetDateTime createdAt) {}

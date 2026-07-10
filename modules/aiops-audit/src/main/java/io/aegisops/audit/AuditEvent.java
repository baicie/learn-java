package io.aegisops.audit;

import java.time.OffsetDateTime;

public record AuditEvent(
    String id,
    String tenantId,
    String actorId,
    String action,
    String resourceType,
    String resourceId,
    String beforeJson,
    String afterJson,
    String detailJson,
    OffsetDateTime createdAt) {}

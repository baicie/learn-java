package io.aegisops.incident;

import java.time.OffsetDateTime;

public record AlertCandidate(
    String id,
    String tenantId,
    String source,
    String sourceEventId,
    String severity,
    String title,
    String description,
    String assetId,
    String entityType,
    String entityName,
    String fingerprint,
    String aggregationKey,
    String labelsJson,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    OffsetDateTime createdAt) {}

package io.aegisops.rca;

import java.time.OffsetDateTime;

public record RcaAlertRecord(
        String id,
        String source,
        String sourceEventId,
        String severity,
        String title,
        String description,
        String assetId,
        String entityType,
        String entityName,
        String fingerprint,
        String labelsJson,
        OffsetDateTime startsAt,
        OffsetDateTime createdAt
) {}

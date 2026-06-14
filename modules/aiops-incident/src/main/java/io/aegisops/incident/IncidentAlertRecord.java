package io.aegisops.incident;

import java.time.OffsetDateTime;

public record IncidentAlertRecord(
        String id,
        String source,
        String sourceEventId,
        String severity,
        String title,
        String status,
        String assetId,
        String entityName,
        String fingerprint,
        OffsetDateTime startsAt,
        String relationType
) {}

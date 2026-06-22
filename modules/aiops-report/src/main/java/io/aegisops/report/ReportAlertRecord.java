package io.aegisops.report;

import java.time.OffsetDateTime;

public record ReportAlertRecord(
    String id,
    String source,
    String sourceEventId,
    String severity,
    String title,
    String description,
    String assetId,
    String entityType,
    String entityName,
    String status,
    String fingerprint,
    String aggregationKey,
    String labelsJson,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    OffsetDateTime createdAt) {}

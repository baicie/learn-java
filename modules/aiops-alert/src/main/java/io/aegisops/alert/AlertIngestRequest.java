package io.aegisops.alert;

import java.time.OffsetDateTime;
import java.util.Map;

/** Standardized alert ingest request for webhook and internal ingestion. */
public record AlertIngestRequest(
    String source,
    String sourceEventId,
    String severity,
    String title,
    String description,
    String assetId,
    String entityType,
    String entityName,
    Map<String, Object> labels,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    String status,
    Map<String, Object> rawPayload) {}

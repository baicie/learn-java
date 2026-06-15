package io.aegisops.ai.client.dto;

import java.time.OffsetDateTime;

public record AiAlertRecord(
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
    OffsetDateTime startsAt) {}

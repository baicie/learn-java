package io.aegisops.incident;

import java.time.OffsetDateTime;

public record IncidentTimelineRecord(
    String id,
    OffsetDateTime eventTime,
    String eventType,
    String title,
    String description,
    String source,
    String payloadJson) {}

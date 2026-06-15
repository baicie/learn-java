package io.aegisops.incident;

import java.time.OffsetDateTime;

public record TimelineCreateCommand(
    String id,
    String incidentId,
    OffsetDateTime eventTime,
    String eventType,
    String title,
    String description,
    String source,
    String payloadJson) {}

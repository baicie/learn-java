package io.aegisops.rca;

import java.time.OffsetDateTime;

/** DTO for adding incident timeline events. */
public record AddTimelineParams(
    String id,
    String incidentId,
    OffsetDateTime eventTime,
    String title,
    String description,
    String payloadJson) {}

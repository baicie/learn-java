package io.aegisops.ai.client.dto;

import java.time.OffsetDateTime;

public record AiTimelineRecord(
    String id,
    OffsetDateTime eventTime,
    String eventType,
    String title,
    String description,
    String source,
    String payloadJson) {}

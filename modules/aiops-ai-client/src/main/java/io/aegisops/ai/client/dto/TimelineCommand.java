package io.aegisops.ai.client.dto;

import java.time.OffsetDateTime;

public record TimelineCommand(
    String id,
    String incidentId,
    OffsetDateTime eventTime,
    String title,
    String description,
    String payloadJson) {}

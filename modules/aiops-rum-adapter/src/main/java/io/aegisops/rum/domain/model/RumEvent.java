package io.aegisops.rum.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public record RumEvent(
    String eventId,
    String eventType,
    String page,
    String sessionId,
    String userHash,
    String errorMessage,
    String traceId,
    String vitalName,
    BigDecimal vitalValue,
    OffsetDateTime occurredAt,
    Map<String, Object> attributes) {
  public RumEvent {
    attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
  }
}

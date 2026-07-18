package io.aegisops.otel.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public record OtelSignal(
    String signalType,
    String sourceId,
    String serviceName,
    String serviceInstanceId,
    String serviceVersion,
    String environment,
    String traceId,
    String spanId,
    String severity,
    String message,
    String metricName,
    BigDecimal metricValue,
    OffsetDateTime occurredAt,
    Map<String, Object> attributes) {
  public OtelSignal {
    attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
  }
}

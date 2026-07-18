package io.aegisops.otel.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.otel.domain.model.OtelSignal;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OtelJsonMapper {
  private final ObjectMapper json;

  public OtelJsonMapper(ObjectMapper json) {
    this.json = json;
  }

  public OtelSignal map(JsonNode payload) {
    List<OtelSignal> signals = mapAll(payload);
    if (signals.isEmpty()) throw new IllegalArgumentException("OTLP envelope contains no signals");
    return signals.getFirst();
  }

  public List<OtelSignal> mapAll(JsonNode payload) {
    List<OtelSignal> standard = mapStandardEnvelope(payload);
    return standard.isEmpty() ? List.of(mapSimplified(payload)) : List.copyOf(standard);
  }

  private OtelSignal mapSimplified(JsonNode payload) {
    Map<String, Object> attributes = attributes(payload.path("resource"));
    JsonNode signal = payload.has("signal") ? payload.path("signal") : payload;
    String type = text(signal, "signalType", "type");
    String serviceName = stringAttribute(attributes, "service.name");
    if (type == null || serviceName == null) {
      throw new IllegalArgumentException("signalType and resource service.name are required");
    }
    OffsetDateTime occurredAt = timestamp(signal.path("occurredAt").asText(null));
    String sourceId = text(signal, "sourceId", "spanId", "traceId");
    if (sourceId == null) throw new IllegalArgumentException("sourceId is required");
    return new OtelSignal(
        type.toLowerCase(),
        sourceId,
        serviceName,
        stringAttribute(attributes, "service.instance.id"),
        stringAttribute(attributes, "service.version"),
        stringAttribute(attributes, "deployment.environment"),
        text(signal, "traceId"),
        text(signal, "spanId"),
        text(signal, "severity"),
        text(signal, "message", "body"),
        text(signal, "metricName", "name"),
        decimal(signal.path("metricValue")),
        occurredAt,
        merged(attributes, objectMap(signal.path("attributes"))));
  }

  private List<OtelSignal> mapStandardEnvelope(JsonNode payload) {
    List<OtelSignal> result = new ArrayList<>();
    payload.path("resourceSpans").forEach(group -> mapSpans(group, result));
    payload.path("resourceLogs").forEach(group -> mapLogs(group, result));
    payload.path("resourceMetrics").forEach(group -> mapMetrics(group, result));
    return result;
  }

  private void mapSpans(JsonNode group, List<OtelSignal> result) {
    Map<String, Object> resource = attributes(group.path("resource"));
    requireServiceName(resource);
    for (JsonNode scope : group.path("scopeSpans")) {
      for (JsonNode span : scope.path("spans")) {
        String traceId = text(span, "traceId"), spanId = text(span, "spanId");
        result.add(
            signal(
                resource,
                new SignalFields(
                    "trace",
                    stableId(spanId, traceId, span.toString()),
                    traceId,
                    spanId,
                    null,
                    text(span, "name"),
                    null,
                    null,
                    timestampNanos(span, "startTimeUnixNano"),
                    signalAttributes(span.path("attributes")))));
      }
    }
  }

  private void mapLogs(JsonNode group, List<OtelSignal> result) {
    Map<String, Object> resource = attributes(group.path("resource"));
    requireServiceName(resource);
    for (JsonNode scope : group.path("scopeLogs")) {
      for (JsonNode log : scope.path("logRecords")) {
        Object bodyValue = scalar(log.path("body"));
        String body = bodyValue == null ? "" : bodyValue.toString();
        result.add(
            signal(
                resource,
                new SignalFields(
                    "log",
                    stableId(text(log, "eventId"), text(log, "traceId"), log.toString()),
                    text(log, "traceId"),
                    text(log, "spanId"),
                    text(log, "severityText"),
                    body,
                    null,
                    null,
                    timestampNanos(log, "timeUnixNano"),
                    signalAttributes(log.path("attributes")))));
      }
    }
  }

  private void mapMetrics(JsonNode group, List<OtelSignal> result) {
    Map<String, Object> resource = attributes(group.path("resource"));
    requireServiceName(resource);
    for (JsonNode scope : group.path("scopeMetrics")) {
      for (JsonNode metric : scope.path("metrics")) {
        JsonNode points =
            metric.has("gauge")
                ? metric.path("gauge").path("dataPoints")
                : metric.path("sum").path("dataPoints");
        for (JsonNode point : points) {
          BigDecimal value = numeric(point);
          if (value == null) continue;
          String metricName = text(metric, "name");
          result.add(
              signal(
                  resource,
                  new SignalFields(
                      "metric",
                      stableId(null, metricName, point.toString()),
                      null,
                      null,
                      null,
                      null,
                      metricName,
                      value,
                      timestampNanos(point, "timeUnixNano"),
                      signalAttributes(point.path("attributes")))));
        }
      }
    }
  }

  private OtelSignal signal(Map<String, Object> resource, SignalFields fields) {
    return new OtelSignal(
        fields.type(),
        fields.sourceId(),
        stringAttribute(resource, "service.name"),
        stringAttribute(resource, "service.instance.id"),
        stringAttribute(resource, "service.version"),
        stringAttribute(resource, "deployment.environment"),
        fields.traceId(),
        fields.spanId(),
        fields.severity(),
        fields.message(),
        fields.metricName(),
        fields.metricValue(),
        fields.occurredAt(),
        merged(resource, fields.attributes()));
  }

  private void requireServiceName(Map<String, Object> resource) {
    if (stringAttribute(resource, "service.name") == null) {
      throw new IllegalArgumentException("resource service.name is required");
    }
  }

  private Map<String, Object> signalAttributes(JsonNode node) {
    if (node.isObject()) return objectMap(node);
    Map<String, Object> result = new LinkedHashMap<>();
    for (JsonNode item : node) {
      String key = item.path("key").asText();
      if (!key.isBlank()) result.put(key, scalar(item.path("value")));
    }
    return result;
  }

  private BigDecimal numeric(JsonNode point) {
    for (String name : List.of("asDouble", "asInt", "value")) {
      JsonNode value = point.path(name);
      if (value.isNumber()) return value.decimalValue();
      if (value.isTextual()) {
        try {
          return new BigDecimal(value.asText());
        } catch (NumberFormatException ignored) {
          // Try the next OTLP numeric representation.
        }
      }
    }
    return null;
  }

  private OffsetDateTime timestampNanos(JsonNode node, String field) {
    String raw = node.path(field).asText("");
    if (raw.isBlank()) return OffsetDateTime.now(ZoneOffset.UTC);
    long nanos = Long.parseLong(raw);
    return OffsetDateTime.ofInstant(
        Instant.ofEpochSecond(nanos / 1_000_000_000L, nanos % 1_000_000_000L), ZoneOffset.UTC);
  }

  private String stableId(String preferred, String secondary, String raw) {
    if (preferred != null && !preferred.isBlank()) return preferred;
    try {
      String seed = (secondary == null ? "" : secondary) + ":" + raw;
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot create OTLP source id", exception);
    }
  }

  private Map<String, Object> attributes(JsonNode resource) {
    if (resource.path("attributes").isObject()) return objectMap(resource.path("attributes"));
    Map<String, Object> result = new LinkedHashMap<>();
    for (JsonNode item : resource.path("attributes")) {
      String key = item.path("key").asText();
      JsonNode value = item.path("value");
      if (!key.isBlank()) result.put(key, scalar(value));
    }
    return result;
  }

  private Object scalar(JsonNode node) {
    for (String key : List.of("stringValue", "intValue", "doubleValue", "boolValue")) {
      if (node.has(key)) return json.convertValue(node.path(key), Object.class);
    }
    return json.convertValue(node, Object.class);
  }

  private Map<String, Object> objectMap(JsonNode node) {
    return node.isObject()
        ? json.convertValue(
            node,
            json.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class))
        : Map.of();
  }

  private Map<String, Object> merged(Map<String, Object> left, Map<String, Object> right) {
    Map<String, Object> result = new LinkedHashMap<>(left);
    result.putAll(right);
    return result;
  }

  private String stringAttribute(Map<String, Object> values, String key) {
    Object value = values.get(key);
    return value == null || value.toString().isBlank() ? null : value.toString();
  }

  private String text(JsonNode node, String... names) {
    for (String name : names) {
      String value = node.path(name).asText("").trim();
      if (!value.isEmpty()) return value;
    }
    return null;
  }

  private BigDecimal decimal(JsonNode node) {
    return node.isNumber() ? node.decimalValue() : null;
  }

  private OffsetDateTime timestamp(String value) {
    return value == null || value.isBlank()
        ? OffsetDateTime.now(ZoneOffset.UTC)
        : OffsetDateTime.parse(value);
  }

  private record SignalFields(
      String type,
      String sourceId,
      String traceId,
      String spanId,
      String severity,
      String message,
      String metricName,
      BigDecimal metricValue,
      OffsetDateTime occurredAt,
      Map<String, Object> attributes) {}
}

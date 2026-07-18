package io.aegisops.rum.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.rum.domain.model.RumEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RumEventMapper {
  private final ObjectMapper json;

  public RumEventMapper(ObjectMapper json) {
    this.json = json;
  }

  public RumEvent map(JsonNode node) {
    String id = text(node, "eventId"), type = text(node, "eventType"), page = text(node, "page");
    if (id == null || type == null || page == null)
      throw new IllegalArgumentException("eventId, eventType and page are required");
    requireLength(page, 512, "page");
    requireLength(text(node, "sessionId"), 128, "sessionId");
    String error = text(node, "errorMessage");
    if ("error".equals(type) && error == null)
      throw new IllegalArgumentException("errorMessage is required for error events");
    requireLength(error, 4096, "errorMessage");
    String vitalName = text(node, "vitalName");
    if ("web_vital".equals(type) && (vitalName == null || !node.path("vitalValue").isNumber())) {
      throw new IllegalArgumentException(
          "vitalName and vitalValue are required for web_vital events");
    }
    return new RumEvent(
        id,
        type,
        page,
        text(node, "sessionId"),
        hash(text(node, "userId")),
        error,
        text(node, "traceId"),
        vitalName,
        node.path("vitalValue").isNumber() ? node.path("vitalValue").decimalValue() : null,
        node.hasNonNull("occurredAt")
            ? OffsetDateTime.parse(node.path("occurredAt").asText())
            : OffsetDateTime.now(ZoneOffset.UTC),
        objectMap(node.path("attributes")));
  }

  private void requireLength(String value, int max, String field) {
    if (value != null && value.length() > max) {
      throw new IllegalArgumentException(field + " exceeds " + max + " characters");
    }
  }

  private String hash(String value) {
    if (value == null) return null;
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String text(JsonNode n, String k) {
    String v = n.path(k).asText("").trim();
    return v.isEmpty() ? null : v;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> objectMap(JsonNode n) {
    return n.isObject() ? json.convertValue(n, LinkedHashMap.class) : Map.of();
  }
}

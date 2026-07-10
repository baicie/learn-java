package io.aegisops.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Audit-side JSON utilities: serialization, sensitive-field redaction, change diff, size limits.
 * The audit module must not depend on ad-hoc JSON string concatenation.
 */
@Component
public class AuditJson {
  private static final int MAX_JSON_BYTES = 1024 * 1024;

  private static final int MAX_CHANGE_COUNT = 200;

  private static final Set<String> SENSITIVE_KEYS =
      Set.of(
          "password",
          "passwordhash",
          "secret",
          "clientsecret",
          "token",
          "accesstoken",
          "refreshtoken",
          "authorization",
          "privatekey");

  private final ObjectMapper objectMapper;

  public AuditJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    JsonNode node = toNode(value);
    return serialize(redact(node));
  }

  public String normalizeJson(String raw) {
    if (raw == null || raw.isBlank()) {
      return "{}";
    }

    try {
      JsonNode node = objectMapper.readTree(raw);
      if (node == null) {
        return "{}";
      }
      return serialize(redact(node));
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("invalid audit json", ex);
    }
  }

  public String detail(Map<String, Object> attributes, Object before, Object after) {
    ObjectNode root = objectMapper.createObjectNode();

    if (attributes != null) {
      attributes.forEach((key, value) -> root.set(key, toNode(value)));
    }

    ArrayNode changes = objectMapper.createArrayNode();

    for (AuditChange change : diff(before, after)) {
      changes.add(objectMapper.valueToTree(change));
    }

    root.set("changes", changes);
    return serialize(redact(root));
  }

  public List<AuditChange> diff(Object before, Object after) {
    JsonNode beforeNode = redact(toNode(before));
    JsonNode afterNode = redact(toNode(after));

    List<AuditChange> changes = new ArrayList<>();

    collectChanges("", beforeNode, afterNode, changes);

    return List.copyOf(changes);
  }

  public ObjectNode readObject(String raw) {
    if (raw == null || raw.isBlank()) {
      return objectMapper.createObjectNode();
    }

    try {
      JsonNode node = objectMapper.readTree(raw);

      if (node == null || !node.isObject()) {
        return objectMapper.createObjectNode();
      }

      return (ObjectNode) node;
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("invalid audit object json", ex);
    }
  }

  private void collectChanges(
      String path, JsonNode before, JsonNode after, List<AuditChange> changes) {
    if (changes.size() >= MAX_CHANGE_COUNT) {
      return;
    }

    if (before.equals(after)) {
      return;
    }

    if (before.isObject() && after.isObject()) {
      Set<String> names = new LinkedHashSet<>();

      before.fieldNames().forEachRemaining(names::add);
      after.fieldNames().forEachRemaining(names::add);

      for (String name : names) {
        collectChanges(
            path + "/" + escapePath(name),
            valueOrNull(before.get(name)),
            valueOrNull(after.get(name)),
            changes);

        if (changes.size() >= MAX_CHANGE_COUNT) {
          return;
        }
      }

      return;
    }

    // Arrays are treated as a single value to avoid noisy multi-select reorder diffs.
    changes.add(
        new AuditChange(path.isBlank() ? "/" : path, valueOrNull(before), valueOrNull(after)));
  }

  private JsonNode redact(JsonNode source) {
    if (source == null || source.isNull()) {
      return NullNode.getInstance();
    }

    if (source.isObject()) {
      ObjectNode result = objectMapper.createObjectNode();

      source
          .fields()
          .forEachRemaining(
              entry -> {
                if (isSensitive(entry.getKey())) {
                  result.put(entry.getKey(), "***");
                } else {
                  result.set(entry.getKey(), redact(entry.getValue()));
                }
              });

      return result;
    }

    if (source.isArray()) {
      ArrayNode result = objectMapper.createArrayNode();

      source.forEach(value -> result.add(redact(value)));

      return result;
    }

    return source.deepCopy();
  }

  private boolean isSensitive(String key) {
    String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");

    return SENSITIVE_KEYS.contains(normalized);
  }

  private JsonNode toNode(Object value) {
    if (value == null) {
      return objectMapper.createObjectNode();
    }

    if (value instanceof JsonNode node) {
      return node.deepCopy();
    }

    return objectMapper.valueToTree(value);
  }

  private JsonNode valueOrNull(JsonNode value) {
    return value == null ? NullNode.getInstance() : value;
  }

  private String serialize(JsonNode node) {
    try {
      String json = objectMapper.writeValueAsString(node);

      if (json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
        throw new IllegalArgumentException("audit json exceeds 1 MiB");
      }

      return json;
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("failed to serialize audit json", ex);
    }
  }

  private String escapePath(String value) {
    return value.replace("~", "~0").replace("/", "~1");
  }
}

package io.aegisops.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
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
 *
 * <p>All audit snapshots MUST be a JSON object so that the database {@code jsonb_typeof(...)} =
 * 'object'} constraints and the append-only trigger never reject a real audit event at insert time.
 * {@link #normalizeObject(String, String)} is the canonical entry for that contract.
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

    if (!node.isObject()) {
      throw new IllegalArgumentException("audit snapshot must be a JSON object");
    }

    return serialize(redact(node));
  }

  /**
   * Backwards-compatible alias of {@link #normalizeObject(String, String)} that defaults the field
   * name to {@code auditJson}. Throws {@link IllegalArgumentException} if the payload is not a JSON
   * object, preventing database constraint failures at INSERT time.
   */
  public String normalizeJson(String raw) {
    return normalizeObject(raw, "auditJson");
  }

  public String normalizeObject(String raw, String fieldName) {
    if (raw == null || raw.isBlank()) {
      return "{}";
    }

    try {
      JsonNode node = objectMapper.readTree(raw);

      if (node == null || !node.isObject()) {
        throw new IllegalArgumentException(fieldName + " must be a JSON object");
      }

      return serialize(redact(node));
    } catch (IllegalArgumentException ex) {
      throw ex;
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("invalid " + fieldName, ex);
    }
  }

  public String detail(Map<String, Object> attributes, Object before, Object after) {
    ObjectNode root = objectMapper.createObjectNode();

    if (attributes != null) {
      attributes.forEach((key, value) -> root.set(key, toNode(value)));
    }

    DiffAccumulator accumulator = calculateDiff(before, after);

    ArrayNode changes = objectMapper.createArrayNode();
    for (AuditChange change : accumulator.changes) {
      changes.add(objectMapper.valueToTree(change));
    }

    root.set("changes", changes);
    root.put("changesTruncated", accumulator.truncated);

    return serialize(redact(root));
  }

  public List<AuditChange> diff(Object before, Object after) {
    return List.copyOf(calculateDiff(before, after).changes);
  }

  private DiffAccumulator calculateDiff(Object before, Object after) {
    JsonNode beforeNode = toNode(before);
    JsonNode afterNode = toNode(after);

    DiffAccumulator accumulator = new DiffAccumulator();

    collectChanges("", beforeNode, afterNode, false, accumulator);

    return accumulator;
  }

  private void collectChanges(
      String path,
      JsonNode before,
      JsonNode after,
      boolean inheritedSensitive,
      DiffAccumulator accumulator) {
    if (before.equals(after)) {
      return;
    }

    if (accumulator.changes.size() >= MAX_CHANGE_COUNT) {
      accumulator.truncated = true;
      return;
    }

    if (!inheritedSensitive && before.isObject() && after.isObject()) {
      Set<String> names = new LinkedHashSet<>();

      before.fieldNames().forEachRemaining(names::add);
      after.fieldNames().forEachRemaining(names::add);

      for (String name : names) {
        collectChanges(
            path + "/" + escapePath(name),
            valueOrNull(before.get(name)),
            valueOrNull(after.get(name)),
            isSensitive(name),
            accumulator);

        if (accumulator.changes.size() >= MAX_CHANGE_COUNT) {
          accumulator.truncated = true;
          return;
        }
      }

      return;
    }

    accumulator.changes.add(
        new AuditChange(
            path.isBlank() ? "/" : path,
            auditValue(before, inheritedSensitive),
            auditValue(after, inheritedSensitive)));
  }

  private JsonNode auditValue(JsonNode value, boolean sensitive) {
    if (value == null || value.isNull()) {
      return NullNode.getInstance();
    }

    if (sensitive) {
      return TextNode.valueOf("***");
    }

    return redact(value);
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
    if (key == null) {
      return false;
    }

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

  public ObjectNode readObject(String raw) {
    if (raw == null || raw.isBlank()) {
      return objectMapper.createObjectNode();
    }

    try {
      JsonNode node = objectMapper.readTree(raw);

      if (node == null || !node.isObject()) {
        throw new IllegalArgumentException("audit object json must be a JSON object");
      }

      return (ObjectNode) node;
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("invalid audit object json", ex);
    }
  }

  private static final class DiffAccumulator {
    private final List<AuditChange> changes = new ArrayList<>();
    private boolean truncated;
  }
}

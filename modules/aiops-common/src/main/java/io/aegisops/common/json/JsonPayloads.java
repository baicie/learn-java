package io.aegisops.common.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;

/** 应用层 JSON 字段校验工具，避免非法 JSON 下沉为数据库异常。 */
public final class JsonPayloads {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Set<String> OBJECT_FIELDS =
      Set.of(
          "builtinDataJson",
          "customDataJson",
          "schemaJson",
          "extraJson",
          "payloadJson",
          "detailJson");

  private JsonPayloads() {}

  /** 将空白视为 null；否则校验为合法 JSON。 */
  public static String normalizeObject(String value, String field) {
    if (value == null || value.isBlank()) {
      return "{}";
    }
    validateJson(value, field, true);
    return value;
  }

  public static String normalizeArray(String value, String field) {
    if (value == null || value.isBlank()) {
      return "[]";
    }
    validateJson(value, field, false);
    return value;
  }

  /** 严格校验：返回必填字段是否为合法 JSON 对象；非法时抛 {@link IllegalArgumentException}。 */
  public static void validateJson(String value, String field, boolean requireObject) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    try {
      JsonNode node = MAPPER.readTree(value);
      if (requireObject && !node.isObject()) {
        throw new IllegalArgumentException(field + " must be a JSON object");
      }
      if (!requireObject && !node.isArray()) {
        throw new IllegalArgumentException(field + " must be a JSON array");
      }
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException(field + " is not valid JSON: " + e.getMessage());
    }
  }

  public static boolean isKnownObjectField(String field) {
    return OBJECT_FIELDS.contains(field);
  }
}

package io.aegisops.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.util.Map;

public class ExecutionJson {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  public ExecutionJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      if (value == null) {
        return "{}";
      }
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new AppException("EXECUTION_JSON_WRITE_FAILED", "Failed to serialize execution json");
    }
  }

  public Map<String, Object> readMap(String json) {
    try {
      if (json == null || json.isBlank()) {
        return Map.of();
      }
      return objectMapper.readValue(json, MAP_TYPE);
    } catch (Exception ex) {
      return Map.of();
    }
  }

  public String textValue(String json, String key) {
    Object value = readMap(json).get(key);
    return value == null ? "" : String.valueOf(value);
  }

  public boolean booleanValue(String json, String key, boolean fallback) {
    Object value = readMap(json).get(key);
    if (value instanceof Boolean bool) {
      return bool;
    }
    return fallback;
  }
}

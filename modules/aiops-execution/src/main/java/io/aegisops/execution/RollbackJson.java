package io.aegisops.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.util.Map;

public class RollbackJson {
  private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  public RollbackJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      if (value == null) {
        return "{}";
      }
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new AppException("ROLLBACK_JSON_WRITE_FAILED", "Failed to serialize rollback json");
    }
  }

  public Map<String, Object> readObjectMap(String json) {
    try {
      if (json == null || json.isBlank()) {
        return Map.of();
      }
      return objectMapper.readValue(json, OBJECT_MAP);
    } catch (Exception ex) {
      return Map.of();
    }
  }
}

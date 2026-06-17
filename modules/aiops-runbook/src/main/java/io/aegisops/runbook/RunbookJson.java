package io.aegisops.runbook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.util.List;
import java.util.Map;

/** JSON helpers for runbook / plan payloads. Defensive reads return empty collections. */
public class RunbookJson {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  public RunbookJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      if (value == null) {
        return "{}";
      }
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new AppException("RUNBOOK_JSON_WRITE_FAILED", "Failed to serialize runbook json");
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

  public List<String> readStringList(Object value) {
    if (value == null) {
      return List.of();
    }

    try {
      return objectMapper.convertValue(value, STRING_LIST_TYPE).stream()
          .filter(item -> item != null && !item.isBlank())
          .map(String::trim)
          .toList();
    } catch (Exception ex) {
      return List.of();
    }
  }
}

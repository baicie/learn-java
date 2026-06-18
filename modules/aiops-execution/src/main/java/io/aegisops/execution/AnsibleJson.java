package io.aegisops.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.util.List;
import java.util.Map;

public class AnsibleJson {
  private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  public AnsibleJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      if (value == null) {
        return "{}";
      }
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new AppException("ANSIBLE_JSON_WRITE_FAILED", "Failed to serialize ansible json");
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

  public List<String> readStringList(String json) {
    try {
      if (json == null || json.isBlank()) {
        return List.of();
      }
      return objectMapper.readValue(json, STRING_LIST);
    } catch (Exception ex) {
      return List.of();
    }
  }
}

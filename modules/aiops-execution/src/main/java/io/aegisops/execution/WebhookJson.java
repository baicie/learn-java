package io.aegisops.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.util.List;
import java.util.Map;

public class WebhookJson {
  private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  public WebhookJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      if (value == null) {
        return "{}";
      }
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new AppException("WEBHOOK_JSON_WRITE_FAILED", "Failed to serialize webhook json");
    }
  }

  public Map<String, String> readStringMap(String json) {
    try {
      if (json == null || json.isBlank()) {
        return Map.of();
      }
      return objectMapper.readValue(json, STRING_MAP);
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

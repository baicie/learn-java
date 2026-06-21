package io.aegisops.plugin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PluginJson {
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

  private static final TypeReference<List<Object>> LIST = new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  public PluginJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (Exception ex) {
      throw new AppException("PLUGIN_JSON_WRITE_FAILED", "Failed to serialize plugin json");
    }
  }

  public Map<String, Object> readMap(String json) {
    try {
      if (json == null || json.isBlank()) {
        return Map.of();
      }
      Map<String, Object> value = objectMapper.readValue(json, MAP);
      return value == null ? Map.of() : value;
    } catch (Exception ex) {
      throw new AppException("PLUGIN_JSON_READ_FAILED", "Failed to parse plugin json object");
    }
  }

  public List<Object> readList(String json) {
    try {
      if (json == null || json.isBlank()) {
        return List.of();
      }
      List<Object> value = objectMapper.readValue(json, LIST);
      return value == null ? List.of() : value;
    } catch (Exception ex) {
      throw new AppException("PLUGIN_JSON_READ_FAILED", "Failed to parse plugin json array");
    }
  }
}

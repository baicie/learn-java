package io.aegisops.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.util.List;

public class KnowledgeBaseJson {
  private static final TypeReference<List<Double>> DOUBLE_LIST = new TypeReference<>() {};
  private final ObjectMapper objectMapper;

  public KnowledgeBaseJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      if (value == null) {
        return "{}";
      }
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new AppException("KB_JSON_WRITE_FAILED", "Failed to serialize knowledge base json");
    }
  }

  public List<Double> readDoubleList(String json) {
    try {
      if (json == null || json.isBlank()) {
        return List.of();
      }
      return objectMapper.readValue(json, DOUBLE_LIST);
    } catch (Exception ex) {
      return List.of();
    }
  }
}

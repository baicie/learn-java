package io.aegisops.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AgentEvalJson {
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
  private final ObjectMapper objectMapper;

  public AgentEvalJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      if (value == null) {
        return "{}";
      }
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new AppException("AGENT_EVAL_JSON_WRITE_FAILED", "Failed to serialize agent eval json");
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

package io.aegisops.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.aegisops.common.exception.AppException;

public class PostmortemJson {
  private final ObjectMapper objectMapper;

  public PostmortemJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public static ObjectMapper defaultMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    return mapper;
  }

  public String write(Object value) {
    try {
      if (value == null) {
        return "{}";
      }
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new AppException("POSTMORTEM_JSON_WRITE_FAILED", "Failed to serialize postmortem json");
    }
  }
}

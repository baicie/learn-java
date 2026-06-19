package io.aegisops.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;

public class ExecutionReportJson {
  private final ObjectMapper objectMapper;

  public ExecutionReportJson(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String write(Object value) {
    try {
      if (value == null) {
        return "{}";
      }
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new AppException(
          "EXECUTION_REPORT_JSON_WRITE_FAILED", "Failed to serialize execution report json");
    }
  }
}

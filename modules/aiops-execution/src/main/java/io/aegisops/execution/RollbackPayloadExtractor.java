package io.aegisops.execution;

import io.aegisops.common.exception.AppException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RollbackPayloadExtractor {
  private final RollbackJson json;

  public RollbackPayloadExtractor(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
    this.json = new RollbackJson(objectMapper);
  }

  public RollbackPayload extract(String actionPayloadJson) {
    Map<String, Object> payload = json.readObjectMap(actionPayloadJson);
    Object rollbackValue = payload.get("rollback");

    if (!(rollbackValue instanceof Map<?, ?> rawRollback)) {
      return null;
    }

    Map<String, Object> rollback =
        rawRollback.entrySet().stream()
            .collect(
                Collectors.toMap(entry -> String.valueOf(entry.getKey()), Map.Entry::getValue));

    String title = stringValue(rollback.get("title"));
    String description = stringValue(rollback.get("description"));
    String actionType = stringValue(rollback.get("actionType"));
    String targetType = stringValue(rollback.get("targetType"));

    Object rollbackActionPayload = rollback.get("actionPayload");
    if (actionType.isBlank()) {
      throw new AppException("ROLLBACK_ACTION_TYPE_REQUIRED", "Rollback actionType is required");
    }

    if (!(rollbackActionPayload instanceof Map<?, ?>)) {
      throw new AppException(
          "ROLLBACK_ACTION_PAYLOAD_REQUIRED", "Rollback actionPayload is required");
    }

    return new RollbackPayload(
        title.isBlank() ? "Rollback step" : title,
        description,
        actionType,
        targetType.isBlank() ? "unknown" : targetType,
        json.write(rollbackActionPayload));
  }

  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }

  public record RollbackPayload(
      String title,
      String description,
      String actionType,
      String targetType,
      String actionPayloadJson) {}
}

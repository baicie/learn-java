package io.aegisops.runner.executor.webhook;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.util.Map;

public record WebhookActionPayload(
    String connectorId, String method, String path, Map<String, String> headers, String body) {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  public static WebhookActionPayload parse(ObjectMapper objectMapper, String json) {
    try {
      Map<String, Object> map =
          objectMapper.readValue(json == null || json.isBlank() ? "{}" : json, MAP_TYPE);

      String connectorId = stringValue(map.get("connectorId"));
      if (connectorId.isBlank()) {
        throw new AppException("WEBHOOK_CONNECTOR_ID_REQUIRED", "Webhook connectorId is required");
      }

      Object headersValue = map.get("headers");
      Map<String, String> headers =
          headersValue instanceof Map<?, ?> raw
              ? raw.entrySet().stream()
                  .collect(
                      java.util.stream.Collectors.toMap(
                          item -> String.valueOf(item.getKey()),
                          item -> item.getValue() == null ? "" : String.valueOf(item.getValue())))
              : Map.of();

      Object bodyValue = map.get("body");
      String body =
          bodyValue == null
              ? ""
              : bodyValue instanceof String text
                  ? text
                  : objectMapper.writeValueAsString(bodyValue);

      return new WebhookActionPayload(
          connectorId, stringValue(map.get("method")), stringValue(map.get("path")), headers, body);
    } catch (AppException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AppException("WEBHOOK_ACTION_PAYLOAD_INVALID", "Invalid webhook action payload");
    }
  }

  private static String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }
}

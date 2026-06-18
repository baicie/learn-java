package io.aegisops.runner.executor.ansible;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.util.List;
import java.util.Map;

public record AnsibleActionPayload(
    String inventoryId,
    String playbookId,
    String credentialRefId,
    Boolean checkMode,
    List<String> tags,
    Map<String, Object> extraVars) {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  @SuppressWarnings("unchecked")
  public static AnsibleActionPayload parse(ObjectMapper objectMapper, String json) {
    try {
      Map<String, Object> map =
          objectMapper.readValue(json == null || json.isBlank() ? "{}" : json, MAP_TYPE);

      String inventoryId = stringValue(map.get("inventoryId"));
      String playbookId = stringValue(map.get("playbookId"));
      String credentialRefId = stringValue(map.get("credentialRefId"));

      if (inventoryId.isBlank()) {
        throw new AppException("ANSIBLE_INVENTORY_ID_REQUIRED", "Ansible inventoryId is required");
      }

      if (playbookId.isBlank()) {
        throw new AppException("ANSIBLE_PLAYBOOK_ID_REQUIRED", "Ansible playbookId is required");
      }

      Object tagsValue = map.get("tags");
      List<String> tags =
          tagsValue instanceof List<?> raw
              ? raw.stream()
                  .map(String::valueOf)
                  .map(String::trim)
                  .filter(item -> !item.isBlank())
                  .toList()
              : List.of();

      Object varsValue = map.get("extraVars");
      Map<String, Object> vars =
          varsValue instanceof Map<?, ?> raw
              ? raw.entrySet().stream()
                  .collect(
                      java.util.stream.Collectors.toMap(
                          item -> String.valueOf(item.getKey()), Map.Entry::getValue))
              : Map.of();

      Object checkValue = map.get("checkMode");
      Boolean checkMode = checkValue instanceof Boolean bool ? bool : null;

      return new AnsibleActionPayload(
          inventoryId,
          playbookId,
          credentialRefId.isBlank() ? null : credentialRefId,
          checkMode,
          tags,
          vars);
    } catch (AppException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AppException("ANSIBLE_ACTION_PAYLOAD_INVALID", "Invalid Ansible action payload");
    }
  }

  private static String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }
}

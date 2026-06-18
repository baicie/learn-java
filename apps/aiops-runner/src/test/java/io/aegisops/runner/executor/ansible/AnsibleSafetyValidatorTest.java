package io.aegisops.runner.executor.ansible;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.AnsibleJson;
import io.aegisops.execution.dto.AnsibleInventoryRecord;
import io.aegisops.execution.dto.AnsiblePlaybookRecord;
import io.aegisops.execution.dto.AnsiblePolicyRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnsibleSafetyValidatorTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AnsibleJson json = new AnsibleJson(objectMapper);
  private final AnsibleSafetyValidator validator = new AnsibleSafetyValidator(objectMapper);

  @Test
  void allowValidDryRun() {
    assertDoesNotThrow(
        () ->
            validator.validateDryRun(
                inventory(true),
                playbook(true, List.of("restart")),
                policy(true, List.of("inv_1"), List.of("service_name")),
                new AnsibleActionPayload(
                    "inv_1",
                    "pb_1",
                    true,
                    List.of("restart"),
                    Map.of("service_name", "order-service"))));
  }

  @Test
  void rejectDisabledInventory() {
    assertThrows(
        AppException.class,
        () ->
            validator.validateDryRun(
                inventory(false),
                playbook(true, List.of("restart")),
                policy(true, List.of("inv_1"), List.of("service_name")),
                new AnsibleActionPayload("inv_1", "pb_1", true, List.of(), Map.of())));
  }

  @Test
  void rejectInventoryNotAllowed() {
    assertThrows(
        AppException.class,
        () ->
            validator.validateDryRun(
                inventory(true),
                playbook(true, List.of("restart")),
                policy(true, List.of("other_inv"), List.of("service_name")),
                new AnsibleActionPayload("inv_1", "pb_1", true, List.of(), Map.of())));
  }

  @Test
  void rejectDisallowedTag() {
    assertThrows(
        AppException.class,
        () ->
            validator.validateDryRun(
                inventory(true),
                playbook(true, List.of("restart")),
                policy(true, List.of("inv_1"), List.of("service_name")),
                new AnsibleActionPayload("inv_1", "pb_1", true, List.of("delete"), Map.of())));
  }

  @Test
  void rejectSecretLikeExtraVar() {
    assertThrows(
        AppException.class,
        () ->
            validator.validateDryRun(
                inventory(true),
                playbook(true, List.of("restart")),
                policy(true, List.of("inv_1"), List.of("service_name", "password")),
                new AnsibleActionPayload(
                    "inv_1", "pb_1", true, List.of(), Map.of("password", "123456"))));
  }

  private AnsibleInventoryRecord inventory(boolean enabled) {
    return new AnsibleInventoryRecord(
        "inv_1",
        "tenant_1",
        "prod",
        "desc",
        "inline",
        "[web]\n10.0.0.1",
        null,
        enabled,
        "alice",
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private AnsiblePlaybookRecord playbook(boolean enabled, List<String> allowedTags) {
    return new AnsiblePlaybookRecord(
        "pb_1",
        "tenant_1",
        "restart",
        "desc",
        "playbooks/restart.yml",
        null,
        "{}",
        json.write(allowedTags),
        enabled,
        "alice",
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private AnsiblePolicyRecord policy(
      boolean enabled, List<String> inventories, List<String> extraVars) {
    return new AnsiblePolicyRecord(
        "apol_1",
        "tenant_1",
        "pb_1",
        false,
        true,
        json.write(inventories),
        json.write(extraVars),
        32768,
        1800,
        enabled,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}

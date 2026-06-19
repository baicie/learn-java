package io.aegisops.runner.executor.ansible;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.AnsibleJson;
import io.aegisops.execution.dto.AnsibleCredentialRecord;
import io.aegisops.execution.dto.AnsibleInventoryRecord;
import io.aegisops.execution.dto.AnsiblePlaybookRecord;
import io.aegisops.execution.dto.AnsiblePolicyRecord;
import io.aegisops.execution.dto.ExecutionRunRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnsibleSafetyValidatorTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AnsibleJson json = new AnsibleJson(objectMapper);
  private final AnsibleSafetyValidator validator = new AnsibleSafetyValidator(objectMapper);

  @Test
  void allowValidDryRunPreview() {
    assertDoesNotThrow(
        () ->
            validator.validateDryRunPreview(
                inventory(true),
                playbook(true, List.of("restart")),
                policy(true, true, List.of("inv_1"), List.of("service_name")),
                new AnsibleActionPayload(
                    "inv_1",
                    "pb_1",
                    null,
                    null,
                    List.of("restart"),
                    Map.of("service_name", "order-service"))));
  }

  @Test
  void allowValidCheckExecution() {
    assertDoesNotThrow(
        () ->
            validator.validateCheckExecution(
                inventory(true),
                playbook(true, List.of("restart")),
                policy(true, true, List.of("inv_1"), List.of("service_name")),
                new AnsibleActionPayload(
                    "inv_1",
                    "pb_1",
                    null,
                    null,
                    List.of("restart"),
                    Map.of("service_name", "order-service"))));
  }

  @Test
  void rejectDisabledInventory() {
    assertThrows(
        AppException.class,
        () ->
            validator.validateCheckExecution(
                inventory(false),
                playbook(true, List.of("restart")),
                policy(true, true, List.of("inv_1"), List.of("service_name")),
                new AnsibleActionPayload("inv_1", "pb_1", null, null, List.of(), Map.of())));
  }

  @Test
  void rejectInventoryNotAllowed() {
    assertThrows(
        AppException.class,
        () ->
            validator.validateCheckExecution(
                inventory(true),
                playbook(true, List.of("restart")),
                policy(true, true, List.of("other_inv"), List.of("service_name")),
                new AnsibleActionPayload("inv_1", "pb_1", null, null, List.of(), Map.of())));
  }

  @Test
  void rejectDisallowedTag() {
    assertThrows(
        AppException.class,
        () ->
            validator.validateCheckExecution(
                inventory(true),
                playbook(true, List.of("restart")),
                policy(true, true, List.of("inv_1"), List.of("service_name")),
                new AnsibleActionPayload(
                    "inv_1", "pb_1", null, null, List.of("delete"), Map.of())));
  }

  @Test
  void rejectSecretLikeExtraVar() {
    assertThrows(
        AppException.class,
        () ->
            validator.validateCheckExecution(
                inventory(true),
                playbook(true, List.of("restart")),
                policy(true, true, List.of("inv_1"), List.of("service_name", "password")),
                new AnsibleActionPayload(
                    "inv_1", "pb_1", null, null, List.of(), Map.of("password", "123456"))));
  }

  @Test
  void rejectSecretLikeExtraVarBySubstring() {
    assertThrows(
        AppException.class,
        () ->
            validator.validateCheckExecution(
                inventory(true),
                playbook(true, List.of("restart")),
                policy(true, true, List.of("inv_1"), List.of("service_name", "db_password")),
                new AnsibleActionPayload(
                    "inv_1", "pb_1", null, null, List.of(), Map.of("db_password", "123456"))));
  }

  @Test
  void rejectApiTokenExtraVarBySubstring() {
    assertThrows(
        AppException.class,
        () ->
            validator.validateCheckExecution(
                inventory(true),
                playbook(true, List.of("restart")),
                policy(true, true, List.of("inv_1"), List.of("service_name", "api_token")),
                new AnsibleActionPayload(
                    "inv_1", "pb_1", null, null, List.of(), Map.of("api_token", "secret-token"))));
  }

  @Test
  void rejectCheckExecutionWhenPolicyDisabled() {
    assertThrows(
        AppException.class,
        () ->
            validator.validateCheckExecution(
                inventory(true),
                playbook(true, List.of("restart")),
                policy(true, false, List.of("inv_1"), List.of("service_name")),
                new AnsibleActionPayload(
                    "inv_1",
                    "pb_1",
                    null,
                    null,
                    List.of("restart"),
                    Map.of("service_name", "order-service"))));
  }

  @Test
  void rejectLiveExecutionWhenNotAllowed() {
    ExecutionRunRecord run = runRecord(null, null, null);
    assertThrows(
        AppException.class,
        () -> validator.validateLive(liveInputs(false, false, List.of("low"), null), run, null));
  }

  @Test
  void rejectLiveExecutionWithoutApprovalSnapshot() {
    ExecutionRunRecord run = runRecord(null, null, "medium");
    assertThrows(
        AppException.class,
        () ->
            validator.validateLive(
                liveInputs(true, true, List.of("low", "medium"), null), run, null));
  }

  @Test
  void rejectLiveExecutionWithWrongRiskLevel() {
    ExecutionRunRecord run =
        runRecord(
            "appr_1",
            "{\"approvalId\":\"appr_1\",\"planId\":\"plan_1\",\"status\":\"approved\",\"requiredApprovals\":1,\"approvedCount\":1}",
            "critical");
    assertThrows(
        AppException.class,
        () -> validator.validateLive(liveInputs(true, true, List.of("low"), null), run, null));
  }

  @Test
  void allowLiveExecutionWithApprovalAndCorrectRisk() {
    ExecutionRunRecord run =
        runRecord(
            "appr_1",
            "{\"approvalId\":\"appr_1\",\"planId\":\"plan_1\",\"status\":\"approved\",\"requiredApprovals\":1,\"approvedCount\":1}",
            "medium");
    assertDoesNotThrow(
        () ->
            validator.validateLive(
                liveInputs(true, true, List.of("low", "medium"), null), run, null));
  }

  @Test
  void rejectLiveExecutionWithDisabledCredential() {
    ExecutionRunRecord run =
        runRecord(
            "appr_1",
            "{\"approvalId\":\"appr_1\",\"planId\":\"plan_1\",\"status\":\"approved\",\"requiredApprovals\":1,\"approvedCount\":1}",
            "low");
    AnsibleCredentialRecord cred = credentialRecord(false);
    assertThrows(
        AppException.class,
        () ->
            validator.validateLive(
                liveInputsWithCred(true, true, List.of("low"), "cred_1", "cred_1"), run, cred));
  }

  @Test
  void rejectLiveApprovalSnapshotWithNonApprovedStatus() {
    ExecutionRunRecord run =
        runRecord(
            "appr_1",
            "{\"approvalId\":\"appr_1\",\"planId\":\"plan_1\",\"status\":\"pending\",\"requiredApprovals\":1,\"approvedCount\":1}",
            "medium");
    assertThrows(
        AppException.class,
        () -> validator.validateLive(liveInputs(true, true, List.of("medium"), null), run, null));
  }

  @Test
  void rejectLiveApprovalSnapshotWithMismatchedApprovalId() {
    ExecutionRunRecord run =
        runRecord(
            "appr_1",
            "{\"approvalId\":\"appr_2\",\"planId\":\"plan_1\",\"status\":\"approved\",\"requiredApprovals\":1,\"approvedCount\":1}",
            "medium");
    assertThrows(
        AppException.class,
        () -> validator.validateLive(liveInputs(true, true, List.of("medium"), null), run, null));
  }

  @Test
  void rejectLiveApprovalSnapshotWithMismatchedPlanId() {
    ExecutionRunRecord run =
        runRecord(
            "appr_1",
            "{\"approvalId\":\"appr_1\",\"planId\":\"plan_2\",\"status\":\"approved\",\"requiredApprovals\":1,\"approvedCount\":1}",
            "medium");
    assertThrows(
        AppException.class,
        () -> validator.validateLive(liveInputs(true, true, List.of("medium"), null), run, null));
  }

  @Test
  void rejectLiveApprovalSnapshotWithInsufficientApprovals() {
    ExecutionRunRecord run =
        runRecord(
            "appr_1",
            "{\"approvalId\":\"appr_1\",\"planId\":\"plan_1\",\"status\":\"approved\",\"requiredApprovals\":2,\"approvedCount\":1}",
            "medium");
    assertThrows(
        AppException.class,
        () -> validator.validateLive(liveInputs(true, true, List.of("medium"), null), run, null));
  }

  @Test
  void allowLiveWithValidApprovalSnapshot() {
    ExecutionRunRecord run =
        runRecord(
            "appr_1",
            "{\"approvalId\":\"appr_1\",\"planId\":\"plan_1\",\"status\":\"approved\",\"requiredApprovals\":1,\"approvedCount\":1}",
            "medium");
    assertDoesNotThrow(
        () -> validator.validateLive(liveInputs(true, true, List.of("medium"), null), run, null));
  }

  private ExecutionRunRecord runRecord(String approvalId, String snapshot, String riskLevel) {
    return new ExecutionRunRecord(
        "run_1",
        "tenant_1",
        "inc_1",
        "plan_1",
        "running",
        "live",
        "alice",
        "runner_1",
        OffsetDateTime.now(),
        null,
        null,
        null,
        1,
        1,
        null,
        OffsetDateTime.now().plusSeconds(60),
        OffsetDateTime.now(),
        1800,
        approvalId,
        snapshot,
        riskLevel,
        null,
        "normal",
        null,
        null,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private AnsibleCredentialRecord credentialRecord(boolean enabled) {
    return new AnsibleCredentialRecord(
        "cred_1",
        "tenant_1",
        "test",
        "desc",
        "vault",
        "vault://secret/test",
        enabled,
        "alice",
        OffsetDateTime.now(),
        OffsetDateTime.now());
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
      boolean enabled,
      boolean allowCheckExecution,
      List<String> inventories,
      List<String> extraVars) {
    return new AnsiblePolicyRecord(
        "apol_1",
        "tenant_1",
        "pb_1",
        false,
        allowCheckExecution,
        false,
        true,
        json.write(inventories),
        json.write(extraVars),
        "[]",
        "[]",
        false,
        32768,
        1800,
        3600,
        enabled,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private AnsibleInputs liveInputs(
      boolean enabled,
      boolean liveRequiresApproval,
      List<String> allowedRiskLevels,
      String credentialId) {
    AnsiblePolicyRecord policy;
    if (credentialId == null) {
      policy = policyLive(enabled, liveRequiresApproval, allowedRiskLevels);
    } else {
      policy =
          policyLiveWithCredential(enabled, liveRequiresApproval, allowedRiskLevels, credentialId);
    }
    AnsibleActionPayload payload =
        credentialId == null
            ? new AnsibleActionPayload("inv_1", "pb_1", null, null, List.of(), Map.of())
            : new AnsibleActionPayload("inv_1", "pb_1", credentialId, null, List.of(), Map.of());
    return new AnsibleInputs(inventory(true), playbook(true, List.of("restart")), policy, payload);
  }

  private AnsibleInputs liveInputsWithCred(
      boolean enabled,
      boolean liveRequiresApproval,
      List<String> allowedRiskLevels,
      String credentialId,
      String payloadCredentialId) {
    AnsiblePolicyRecord policy =
        policyLiveWithCredential(enabled, liveRequiresApproval, allowedRiskLevels, credentialId);
    AnsibleActionPayload payload =
        new AnsibleActionPayload("inv_1", "pb_1", payloadCredentialId, null, List.of(), Map.of());
    return new AnsibleInputs(inventory(true), playbook(true, List.of("restart")), policy, payload);
  }

  private AnsiblePolicyRecord policyLive(
      boolean enabled, boolean liveRequiresApproval, List<String> allowedRiskLevels) {
    return new AnsiblePolicyRecord(
        "apol_1",
        "tenant_1",
        "pb_1",
        enabled,
        true,
        liveRequiresApproval,
        true,
        json.write(List.of("inv_1")),
        json.write(List.of("service_name")),
        json.write(allowedRiskLevels),
        "[]",
        false,
        32768,
        1800,
        3600,
        enabled,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private AnsiblePolicyRecord policyLiveWithCredential(
      boolean enabled,
      boolean liveRequiresApproval,
      List<String> allowedRiskLevels,
      String credentialId) {
    return new AnsiblePolicyRecord(
        "apol_1",
        "tenant_1",
        "pb_1",
        enabled,
        true,
        liveRequiresApproval,
        true,
        json.write(List.of("inv_1")),
        json.write(List.of("service_name")),
        json.write(allowedRiskLevels),
        json.write(List.of(credentialId)),
        false,
        32768,
        1800,
        3600,
        enabled,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}

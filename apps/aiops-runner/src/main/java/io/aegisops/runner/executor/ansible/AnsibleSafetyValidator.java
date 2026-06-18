package io.aegisops.runner.executor.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.AnsibleJson;
import io.aegisops.execution.dto.AnsibleCredentialRecord;
import io.aegisops.execution.dto.AnsibleInventoryRecord;
import io.aegisops.execution.dto.AnsiblePlaybookRecord;
import io.aegisops.execution.dto.AnsiblePolicyRecord;
import io.aegisops.execution.dto.ExecutionRunRecord;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AnsibleSafetyValidator {
  private static final Set<String> DENIED_EXTRA_VAR_KEY_PARTS =
      Set.of(
          "password",
          "passwd",
          "pwd",
          "token",
          "secret",
          "private_key",
          "ssh_key",
          "api_key",
          "apikey",
          "credential",
          "credentials",
          "vault");

  private final AnsibleJson json;

  public AnsibleSafetyValidator(ObjectMapper objectMapper) {
    this.json = new AnsibleJson(objectMapper);
  }

  public void validateCheckExecution(
      AnsibleInventoryRecord inventory,
      AnsiblePlaybookRecord playbook,
      AnsiblePolicyRecord policy,
      AnsibleActionPayload payload) {
    validateCommon(inventory, playbook, policy, payload);

    if (!policy.allowCheckExecution()) {
      throw new AppException(
          "ANSIBLE_CHECK_EXECUTION_NOT_ALLOWED",
          "Ansible check execution is not allowed by policy");
    }
  }

  public void validateDryRunPreview(
      AnsibleInventoryRecord inventory,
      AnsiblePlaybookRecord playbook,
      AnsiblePolicyRecord policy,
      AnsibleActionPayload payload) {
    validateCommon(inventory, playbook, policy, payload);
  }

  public void validateLive(
      AnsibleInventoryRecord inventory,
      AnsiblePlaybookRecord playbook,
      AnsiblePolicyRecord policy,
      AnsibleActionPayload payload,
      ExecutionRunRecord run,
      AnsibleCredentialRecord credential) {
    validateCommon(inventory, playbook, policy, payload);

    if (!policy.allowLive()) {
      throw new AppException(
          "ANSIBLE_LIVE_NOT_ALLOWED", "Ansible live execution is not allowed by policy");
    }

    if (policy.liveRequiresApproval()) {
      validateApprovalSnapshot(run);
    }

    validateLiveRiskLevel(policy, run.planRiskLevel());
    validateCredentialRef(policy, payload, credential);
  }

  private void validateApprovalSnapshot(ExecutionRunRecord run) {
    if (run.approvalId() == null || run.approvalId().isBlank()) {
      throw new AppException(
          "ANSIBLE_LIVE_APPROVAL_REQUIRED", "Ansible live execution requires approval snapshot");
    }

    if (run.approvalSnapshotJson() == null
        || run.approvalSnapshotJson().isBlank()
        || "{}".equals(run.approvalSnapshotJson())) {
      throw new AppException(
          "ANSIBLE_LIVE_APPROVAL_REQUIRED", "Ansible live execution requires approval snapshot");
    }

    Map<String, Object> snapshot = json.readObjectMap(run.approvalSnapshotJson());

    String snapshotApprovalId = stringValue(snapshot.get("approvalId"));
    String snapshotPlanId = stringValue(snapshot.get("planId"));
    String snapshotStatus = stringValue(snapshot.get("status"));

    if (!Objects.equals(run.approvalId(), snapshotApprovalId)) {
      throw new AppException(
          "ANSIBLE_LIVE_APPROVAL_INVALID",
          "Ansible live approval snapshot does not match execution approval id");
    }

    if (!Objects.equals(run.planId(), snapshotPlanId)) {
      throw new AppException(
          "ANSIBLE_LIVE_APPROVAL_INVALID",
          "Ansible live approval snapshot does not match execution plan id");
    }

    if (!"approved".equalsIgnoreCase(snapshotStatus)) {
      throw new AppException(
          "ANSIBLE_LIVE_APPROVAL_INVALID", "Ansible live approval snapshot is not approved");
    }

    int requiredApprovals = intValue(snapshot.get("requiredApprovals"));
    int approvedCount = intValue(snapshot.get("approvedCount"));

    if (requiredApprovals > 0 && approvedCount < requiredApprovals) {
      throw new AppException(
          "ANSIBLE_LIVE_APPROVAL_INVALID",
          "Ansible live approval snapshot has insufficient approvals");
    }
  }

  private void validateCommon(
      AnsibleInventoryRecord inventory,
      AnsiblePlaybookRecord playbook,
      AnsiblePolicyRecord policy,
      AnsibleActionPayload payload) {
    if (!inventory.enabled()) {
      throw new AppException("ANSIBLE_INVENTORY_DISABLED", "Ansible inventory is disabled");
    }

    if (!playbook.enabled()) {
      throw new AppException("ANSIBLE_PLAYBOOK_DISABLED", "Ansible playbook is disabled");
    }

    if (policy == null || !policy.enabled()) {
      throw new AppException("ANSIBLE_POLICY_DISABLED", "Ansible execution policy is disabled");
    }

    validateInventoryAllowed(policy, inventory.id());
    validateTags(playbook, payload.tags());
    validateExtraVars(policy, payload.extraVars());
  }

  private void validateInventoryAllowed(AnsiblePolicyRecord policy, String inventoryId) {
    List<String> allowed = json.readStringList(policy.allowedInventoryIdsJson());
    if (!allowed.contains(inventoryId)) {
      throw new AppException(
          "ANSIBLE_INVENTORY_NOT_ALLOWED", "Ansible inventory is not allowed by policy");
    }
  }

  private void validateTags(AnsiblePlaybookRecord playbook, List<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return;
    }

    List<String> allowedTags =
        json.readStringList(playbook.allowedTagsJson()).stream()
            .map(item -> item.toLowerCase(Locale.ROOT))
            .toList();

    if (allowedTags.isEmpty()) {
      throw new AppException(
          "ANSIBLE_TAG_NOT_ALLOWED", "Ansible tags are not allowed by this playbook");
    }

    for (String tag : tags) {
      String normalized = tag.toLowerCase(Locale.ROOT);
      if (!allowedTags.contains(normalized)) {
        throw new AppException("ANSIBLE_TAG_NOT_ALLOWED", "Ansible tag is not allowed: " + tag);
      }
    }
  }

  private void validateExtraVars(AnsiblePolicyRecord policy, Map<String, Object> extraVars) {
    if (extraVars == null || extraVars.isEmpty()) {
      return;
    }

    List<String> allowed =
        json.readStringList(policy.allowedExtraVarsJson()).stream()
            .map(item -> item.toLowerCase(Locale.ROOT))
            .toList();

    if (allowed.isEmpty()) {
      throw new AppException(
          "ANSIBLE_EXTRA_VAR_NOT_ALLOWED", "Ansible extraVars are not allowed by policy");
    }

    for (String key : extraVars.keySet()) {
      String normalized = key.toLowerCase(Locale.ROOT);

      if (isDeniedExtraVarKey(normalized)) {
        throw new AppException(
            "ANSIBLE_SECRET_VAR_BLOCKED", "Credential-like extraVar is blocked: " + key);
      }

      if (!allowed.contains(normalized)) {
        throw new AppException(
            "ANSIBLE_EXTRA_VAR_NOT_ALLOWED", "Ansible extraVar is not allowed: " + key);
      }
    }

    int size = json.write(extraVars).getBytes(StandardCharsets.UTF_8).length;
    if (size > policy.maxExtraVarsBytes()) {
      throw new AppException(
          "ANSIBLE_EXTRA_VARS_TOO_LARGE", "Ansible extraVars exceed policy limit");
    }
  }

  private void validateLiveRiskLevel(AnsiblePolicyRecord policy, String riskLevel) {
    String normalizedRisk = riskLevel == null ? "" : riskLevel.toLowerCase(Locale.ROOT);
    List<String> allowed =
        json.readStringList(policy.allowedLiveRiskLevelsJson()).stream()
            .map(item -> item.toLowerCase(Locale.ROOT))
            .toList();

    if (!allowed.contains(normalizedRisk)) {
      throw new AppException(
          "ANSIBLE_LIVE_RISK_NOT_ALLOWED",
          "Ansible live execution is not allowed for risk level: " + riskLevel);
    }
  }

  private void validateCredentialRef(
      AnsiblePolicyRecord policy,
      AnsibleActionPayload payload,
      AnsibleCredentialRecord credential) {
    if (payload.credentialRefId() == null || payload.credentialRefId().isBlank()) {
      return;
    }

    if (credential == null) {
      throw new AppException(
          "ANSIBLE_CREDENTIAL_NOT_FOUND", "Ansible credential reference not found");
    }

    if (!credential.enabled()) {
      throw new AppException(
          "ANSIBLE_CREDENTIAL_DISABLED", "Ansible credential reference is disabled");
    }

    List<String> allowed = json.readStringList(policy.allowedCredentialRefIdsJson());
    if (!allowed.contains(payload.credentialRefId())) {
      throw new AppException(
          "ANSIBLE_CREDENTIAL_NOT_ALLOWED",
          "Ansible credential reference is not allowed by policy");
    }
  }

  private boolean isDeniedExtraVarKey(String normalizedKey) {
    String normalized = normalizedKey.replace("-", "_").toLowerCase(Locale.ROOT);
    for (String part : DENIED_EXTRA_VAR_KEY_PARTS) {
      if (normalized.contains(part)) {
        return true;
      }
    }
    return false;
  }

  private static String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }

  private static int intValue(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value == null) {
      return 0;
    }
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException ex) {
      return 0;
    }
  }
}

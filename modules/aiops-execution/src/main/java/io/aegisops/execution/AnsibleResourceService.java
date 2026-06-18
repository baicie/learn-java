package io.aegisops.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.AnsibleCredentialCreateCommand;
import io.aegisops.execution.dto.AnsibleCredentialCreateRequest;
import io.aegisops.execution.dto.AnsibleCredentialRecord;
import io.aegisops.execution.dto.AnsibleCredentialResponse;
import io.aegisops.execution.dto.AnsibleInventoryCreateCommand;
import io.aegisops.execution.dto.AnsibleInventoryCreateRequest;
import io.aegisops.execution.dto.AnsibleInventoryRecord;
import io.aegisops.execution.dto.AnsibleInventoryResponse;
import io.aegisops.execution.dto.AnsiblePlaybookCreateCommand;
import io.aegisops.execution.dto.AnsiblePlaybookCreateRequest;
import io.aegisops.execution.dto.AnsiblePlaybookRecord;
import io.aegisops.execution.dto.AnsiblePlaybookResponse;
import io.aegisops.execution.dto.AnsiblePolicyCreateCommand;
import io.aegisops.execution.dto.AnsiblePolicyRecord;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnsibleResourceService {
  private final AnsibleRepository repository;
  private final AnsibleJson json;

  public AnsibleResourceService(AnsibleRepository repository, ObjectMapper objectMapper) {
    this.repository = repository;
    this.json = new AnsibleJson(objectMapper);
  }

  public List<AnsibleInventoryResponse> listInventories(String tenantId, boolean includeDisabled) {
    return repository.listInventories(tenantId, includeDisabled).stream()
        .map(this::toInventoryResponse)
        .toList();
  }

  public AnsibleInventoryResponse getInventory(String tenantId, String inventoryId) {
    return repository
        .findInventory(tenantId, inventoryId)
        .map(this::toInventoryResponse)
        .orElseThrow(
            () -> new AppException("ANSIBLE_INVENTORY_NOT_FOUND", "Ansible inventory not found"));
  }

  @Transactional
  public AnsibleInventoryResponse createInventory(
      String tenantId, AnsibleInventoryCreateRequest request) {
    validateInventoryRequest(request);

    String id = newId("inv");
    String type = normalizeInventoryType(request.inventoryType());

    repository.createInventory(
        new AnsibleInventoryCreateCommand(
            id,
            tenantId,
            request.name().trim(),
            request.description(),
            type,
            "inline".equals(type) ? request.inlineInventory() : null,
            "file_ref".equals(type) ? request.fileRef() : null,
            true,
            blankToDefault(request.createdBy(), "system")));

    return getInventory(tenantId, id);
  }

  @Transactional
  public AnsibleInventoryResponse setInventoryEnabled(
      String tenantId, String inventoryId, boolean enabled) {
    boolean updated = repository.setInventoryEnabled(tenantId, inventoryId, enabled);
    if (!updated) {
      throw new AppException(
          "ANSIBLE_INVENTORY_UPDATE_FAILED", "Ansible inventory was not updated");
    }
    return getInventory(tenantId, inventoryId);
  }

  public List<AnsiblePlaybookResponse> listPlaybooks(String tenantId, boolean includeDisabled) {
    return repository.listPlaybooks(tenantId, includeDisabled).stream()
        .map(
            record ->
                toPlaybookResponse(
                    record, repository.findPolicy(tenantId, record.id()).orElse(null)))
        .toList();
  }

  public AnsiblePlaybookResponse getPlaybook(String tenantId, String playbookId) {
    AnsiblePlaybookRecord playbook =
        repository
            .findPlaybook(tenantId, playbookId)
            .orElseThrow(
                () -> new AppException("ANSIBLE_PLAYBOOK_NOT_FOUND", "Ansible playbook not found"));

    return toPlaybookResponse(
        playbook, repository.findPolicy(tenantId, playbook.id()).orElse(null));
  }

  @Transactional
  public AnsiblePlaybookResponse createPlaybook(
      String tenantId, AnsiblePlaybookCreateRequest request) {
    validatePlaybookRequest(request);

    String playbookId = newId("pb");
    String policyId = newId("apol");

    List<String> allowedInventoryIds = resolveAllowedInventoryIds(request);
    validateAllowedInventories(tenantId, allowedInventoryIds);
    validateAllowedCredentials(tenantId, request.allowedCredentialRefIds());

    repository.createPlaybook(
        buildPlaybookCommand(playbookId, tenantId, request, allowedInventoryIds));
    repository.createPolicy(
        buildPolicyCommand(policyId, tenantId, playbookId, request, allowedInventoryIds));

    return getPlaybook(tenantId, playbookId);
  }

  private static List<String> resolveAllowedInventoryIds(AnsiblePlaybookCreateRequest request) {
    return request.allowedInventoryIds() == null ? List.of() : request.allowedInventoryIds();
  }

  private void validateAllowedInventories(String tenantId, List<String> allowedInventoryIds) {
    for (String inventoryId : allowedInventoryIds) {
      AnsibleInventoryRecord inventory =
          repository
              .findInventory(tenantId, inventoryId)
              .orElseThrow(
                  () ->
                      new AppException(
                          "ANSIBLE_INVENTORY_NOT_FOUND",
                          "Allowed inventory not found: " + inventoryId));

      if (!inventory.enabled()) {
        throw new AppException(
            "ANSIBLE_INVENTORY_DISABLED", "Allowed inventory is disabled: " + inventoryId);
      }
    }
  }

  private void validateAllowedCredentials(String tenantId, List<String> allowedCredentialRefIds) {
    if (allowedCredentialRefIds == null) {
      return;
    }
    for (String credentialId : allowedCredentialRefIds) {
      AnsibleCredentialRecord credential =
          repository
              .findCredential(tenantId, credentialId)
              .orElseThrow(
                  () ->
                      new AppException(
                          "ANSIBLE_CREDENTIAL_NOT_FOUND",
                          "Allowed credential reference not found: " + credentialId));

      if (!credential.enabled()) {
        throw new AppException(
            "ANSIBLE_CREDENTIAL_DISABLED",
            "Allowed credential reference is disabled: " + credentialId);
      }
    }
  }

  private AnsiblePlaybookCreateCommand buildPlaybookCommand(
      String playbookId,
      String tenantId,
      AnsiblePlaybookCreateRequest request,
      List<String> allowedInventoryIds) {
    return new AnsiblePlaybookCreateCommand(
        playbookId,
        tenantId,
        request.name().trim(),
        request.description(),
        request.playbookRef(),
        request.playbookContent(),
        json.write(request.variablesSchema() == null ? Map.of() : request.variablesSchema()),
        json.write(request.allowedTags() == null ? List.of() : request.allowedTags()),
        true,
        blankToDefault(request.createdBy(), "system"),
        Boolean.TRUE.equals(request.allowLive()),
        request.allowCheckExecution() == null || request.allowCheckExecution(),
        request.liveRequiresApproval() == null || request.liveRequiresApproval(),
        request.defaultCheckMode() == null || request.defaultCheckMode(),
        json.write(allowedInventoryIds),
        json.write(request.allowedExtraVars() == null ? List.of() : request.allowedExtraVars()),
        json.write(defaultLiveRiskLevels(request)),
        json.write(
            request.allowedCredentialRefIds() == null
                ? List.of()
                : request.allowedCredentialRefIds()),
        request.stdoutStderrMaskingEnabled() == null || request.stdoutStderrMaskingEnabled(),
        normalizeMaxExtraVarsBytes(request.maxExtraVarsBytes()),
        normalizeTimeoutSeconds(request.timeoutSeconds()),
        normalizeTimeoutSeconds(request.liveTimeoutSeconds()));
  }

  private AnsiblePolicyCreateCommand buildPolicyCommand(
      String policyId,
      String tenantId,
      String playbookId,
      AnsiblePlaybookCreateRequest request,
      List<String> allowedInventoryIds) {
    return new AnsiblePolicyCreateCommand(
        policyId,
        tenantId,
        playbookId,
        Boolean.TRUE.equals(request.allowLive()),
        request.allowCheckExecution() == null || request.allowCheckExecution(),
        request.liveRequiresApproval() == null || request.liveRequiresApproval(),
        request.defaultCheckMode() == null || request.defaultCheckMode(),
        json.write(allowedInventoryIds),
        json.write(request.allowedExtraVars() == null ? List.of() : request.allowedExtraVars()),
        json.write(defaultLiveRiskLevels(request)),
        json.write(
            request.allowedCredentialRefIds() == null
                ? List.of()
                : request.allowedCredentialRefIds()),
        request.stdoutStderrMaskingEnabled() == null || request.stdoutStderrMaskingEnabled(),
        normalizeMaxExtraVarsBytes(request.maxExtraVarsBytes()),
        normalizeTimeoutSeconds(request.timeoutSeconds()),
        normalizeTimeoutSeconds(request.liveTimeoutSeconds()),
        true);
  }

  private static List<String> defaultLiveRiskLevels(AnsiblePlaybookCreateRequest request) {
    if (request.allowedLiveRiskLevels() == null) {
      return List.of("low", "medium");
    }
    return request.allowedLiveRiskLevels();
  }

  @Transactional
  public AnsiblePlaybookResponse setPlaybookEnabled(
      String tenantId, String playbookId, boolean enabled) {
    boolean updated = repository.setPlaybookEnabled(tenantId, playbookId, enabled);
    if (!updated) {
      throw new AppException("ANSIBLE_PLAYBOOK_UPDATE_FAILED", "Ansible playbook was not updated");
    }
    return getPlaybook(tenantId, playbookId);
  }

  public List<AnsibleCredentialResponse> listCredentials(String tenantId, boolean includeDisabled) {
    return repository.listCredentials(tenantId, includeDisabled).stream()
        .map(this::toCredentialResponse)
        .toList();
  }

  public AnsibleCredentialResponse getCredential(String tenantId, String credentialId) {
    return repository
        .findCredential(tenantId, credentialId)
        .map(this::toCredentialResponse)
        .orElseThrow(
            () ->
                new AppException(
                    "ANSIBLE_CREDENTIAL_NOT_FOUND", "Ansible credential reference not found"));
  }

  @Transactional
  public AnsibleCredentialResponse createCredential(
      String tenantId, AnsibleCredentialCreateRequest request) {
    validateCredentialRequest(request);

    String id = newId("acred");

    repository.createCredential(
        new AnsibleCredentialCreateCommand(
            id,
            tenantId,
            request.name().trim(),
            request.description(),
            normalizeCredentialType(request.credentialType()),
            request.secretRef().trim(),
            true,
            blankToDefault(request.createdBy(), "system")));

    return getCredential(tenantId, id);
  }

  @Transactional
  public AnsibleCredentialResponse setCredentialEnabled(
      String tenantId, String credentialId, boolean enabled) {
    boolean updated = repository.setCredentialEnabled(tenantId, credentialId, enabled);
    if (!updated) {
      throw new AppException(
          "ANSIBLE_CREDENTIAL_UPDATE_FAILED", "Ansible credential reference was not updated");
    }
    return getCredential(tenantId, credentialId);
  }

  private void validateCredentialRequest(AnsibleCredentialCreateRequest request) {
    if (request == null) {
      throw new AppException(
          "ANSIBLE_CREDENTIAL_REQUEST_REQUIRED", "Ansible credential request is required");
    }

    if (request.name() == null || request.name().isBlank()) {
      throw new AppException(
          "ANSIBLE_CREDENTIAL_NAME_REQUIRED", "Ansible credential name is required");
    }

    if (request.secretRef() == null || request.secretRef().isBlank()) {
      throw new AppException(
          "ANSIBLE_CREDENTIAL_SECRET_REF_REQUIRED", "Ansible credential secretRef is required");
    }

    String secretRef = request.secretRef().trim().toLowerCase();
    if (!secretRef.startsWith("vault://")
        && !secretRef.startsWith("kms://")
        && !secretRef.startsWith("secrets-manager://")) {
      throw new AppException(
          "ANSIBLE_CREDENTIAL_SECRET_REF_INVALID",
          "Ansible credential secretRef must be an external secret reference");
    }
  }

  private String normalizeCredentialType(String value) {
    String type = value == null || value.isBlank() ? "ssh_key" : value.trim().toLowerCase();
    if (!List.of("ssh_key", "password", "token", "vault_ref").contains(type)) {
      throw new AppException(
          "ANSIBLE_CREDENTIAL_TYPE_INVALID", "Unsupported credential type: " + type);
    }
    return type;
  }

  private AnsibleCredentialResponse toCredentialResponse(AnsibleCredentialRecord record) {
    return new AnsibleCredentialResponse(
        record.id(),
        record.name(),
        record.description(),
        record.credentialType(),
        record.secretRef(),
        record.enabled(),
        record.createdBy(),
        record.createdAt(),
        record.updatedAt());
  }

  private void validateInventoryRequest(AnsibleInventoryCreateRequest request) {
    if (request == null) {
      throw new AppException(
          "ANSIBLE_INVENTORY_REQUEST_REQUIRED", "Ansible inventory request is required");
    }

    if (request.name() == null || request.name().isBlank()) {
      throw new AppException(
          "ANSIBLE_INVENTORY_NAME_REQUIRED", "Ansible inventory name is required");
    }

    String type = normalizeInventoryType(request.inventoryType());
    if ("inline".equals(type)
        && (request.inlineInventory() == null || request.inlineInventory().isBlank())) {
      throw new AppException("ANSIBLE_INVENTORY_INLINE_REQUIRED", "Inline inventory is required");
    }

    if ("file_ref".equals(type) && (request.fileRef() == null || request.fileRef().isBlank())) {
      throw new AppException(
          "ANSIBLE_INVENTORY_FILE_REF_REQUIRED", "Inventory fileRef is required");
    }
  }

  private void validatePlaybookRequest(AnsiblePlaybookCreateRequest request) {
    if (request == null) {
      throw new AppException(
          "ANSIBLE_PLAYBOOK_REQUEST_REQUIRED", "Ansible playbook request is required");
    }

    if (request.name() == null || request.name().isBlank()) {
      throw new AppException("ANSIBLE_PLAYBOOK_NAME_REQUIRED", "Ansible playbook name is required");
    }

    boolean hasRef = request.playbookRef() != null && !request.playbookRef().isBlank();
    boolean hasContent = request.playbookContent() != null && !request.playbookContent().isBlank();

    if (!hasRef && !hasContent) {
      throw new AppException(
          "ANSIBLE_PLAYBOOK_SOURCE_REQUIRED", "playbookRef or playbookContent is required");
    }

    if (request.allowedInventoryIds() == null || request.allowedInventoryIds().isEmpty()) {
      throw new AppException(
          "ANSIBLE_ALLOWED_INVENTORY_REQUIRED", "At least one allowed inventory is required");
    }
  }

  private AnsibleInventoryResponse toInventoryResponse(AnsibleInventoryRecord record) {
    return new AnsibleInventoryResponse(
        record.id(),
        record.name(),
        record.description(),
        record.inventoryType(),
        record.inlineInventory(),
        record.fileRef(),
        record.enabled(),
        record.createdBy(),
        record.createdAt(),
        record.updatedAt());
  }

  private AnsiblePlaybookResponse toPlaybookResponse(
      AnsiblePlaybookRecord playbook, AnsiblePolicyRecord policy) {
    return new AnsiblePlaybookResponse(
        playbook.id(),
        playbook.name(),
        playbook.description(),
        playbook.playbookRef(),
        playbook.playbookContent(),
        json.readObjectMap(playbook.variablesSchemaJson()),
        json.readStringList(playbook.allowedTagsJson()),
        playbook.enabled(),
        policy != null && policy.allowLive(),
        policy == null || policy.allowCheckExecution(),
        policy == null || policy.liveRequiresApproval(),
        policy == null || policy.defaultCheckMode(),
        policy == null ? List.of() : json.readStringList(policy.allowedInventoryIdsJson()),
        policy == null ? List.of() : json.readStringList(policy.allowedExtraVarsJson()),
        policy == null
            ? List.of("low", "medium")
            : json.readStringList(policy.allowedLiveRiskLevelsJson()),
        policy == null ? List.of() : json.readStringList(policy.allowedCredentialRefIdsJson()),
        policy == null || policy.stdoutStderrMaskingEnabled(),
        policy == null ? 32768 : policy.maxExtraVarsBytes(),
        policy == null ? 1800 : policy.timeoutSeconds(),
        policy == null ? 1800 : policy.liveTimeoutSeconds(),
        playbook.createdBy(),
        playbook.createdAt(),
        playbook.updatedAt());
  }

  private String normalizeInventoryType(String value) {
    String type = value == null || value.isBlank() ? "inline" : value.trim().toLowerCase();
    if (!List.of("inline", "file_ref").contains(type)) {
      throw new AppException(
          "ANSIBLE_INVENTORY_TYPE_INVALID", "Unsupported inventory type: " + type);
    }
    return type;
  }

  private int normalizeMaxExtraVarsBytes(Integer value) {
    if (value == null) {
      return 32768;
    }
    return Math.max(0, Math.min(value, 1048576));
  }

  private int normalizeTimeoutSeconds(Integer value) {
    if (value == null) {
      return 1800;
    }
    return Math.max(30, Math.min(value, 86400));
  }

  private String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}

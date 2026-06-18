package io.aegisops.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
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

    List<String> allowedInventoryIds =
        request.allowedInventoryIds() == null ? List.of() : request.allowedInventoryIds();
    for (String inventoryId : allowedInventoryIds) {
      repository
          .findInventory(tenantId, inventoryId)
          .orElseThrow(
              () ->
                  new AppException(
                      "ANSIBLE_INVENTORY_NOT_FOUND",
                      "Allowed inventory not found: " + inventoryId));
    }

    repository.createPlaybook(
        new AnsiblePlaybookCreateCommand(
            playbookId,
            tenantId,
            request.name().trim(),
            request.description(),
            request.playbookRef(),
            request.playbookContent(),
            json.write(request.variablesSchema() == null ? Map.of() : request.variablesSchema()),
            json.write(request.allowedTags() == null ? List.of() : request.allowedTags()),
            true,
            blankToDefault(request.createdBy(), "system")));

    repository.createPolicy(
        new AnsiblePolicyCreateCommand(
            policyId,
            tenantId,
            playbookId,
            Boolean.TRUE.equals(request.allowLive()),
            request.defaultCheckMode() == null || request.defaultCheckMode(),
            json.write(allowedInventoryIds),
            json.write(request.allowedExtraVars() == null ? List.of() : request.allowedExtraVars()),
            normalizeMaxExtraVarsBytes(request.maxExtraVarsBytes()),
            normalizeTimeoutSeconds(request.timeoutSeconds()),
            true));

    return getPlaybook(tenantId, playbookId);
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
        policy == null || policy.defaultCheckMode(),
        policy == null ? List.of() : json.readStringList(policy.allowedInventoryIdsJson()),
        policy == null ? List.of() : json.readStringList(policy.allowedExtraVarsJson()),
        policy == null ? 32768 : policy.maxExtraVarsBytes(),
        policy == null ? 1800 : policy.timeoutSeconds(),
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

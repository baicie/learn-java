package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.AnsibleInventoryCreateCommand;
import io.aegisops.execution.dto.AnsibleInventoryCreateRequest;
import io.aegisops.execution.dto.AnsibleInventoryRecord;
import io.aegisops.execution.dto.AnsiblePlaybookCreateCommand;
import io.aegisops.execution.dto.AnsiblePlaybookCreateRequest;
import io.aegisops.execution.dto.AnsiblePlaybookRecord;
import io.aegisops.execution.dto.AnsiblePolicyCreateCommand;
import io.aegisops.execution.dto.AnsiblePolicyRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnsibleResourceServiceTest {
  @Test
  void createInventoryCreatesInlineInventory() {
    FakeAnsibleRepository repository = new FakeAnsibleRepository();
    AnsibleResourceService service = new AnsibleResourceService(repository, new ObjectMapper());

    var response =
        service.createInventory(
            "tenant_1",
            new AnsibleInventoryCreateRequest(
                "prod", "desc", "inline", "[web]\n10.0.0.1", null, "alice"));

    assertEquals("prod", response.name());
    assertEquals("inline", response.inventoryType());
  }

  @Test
  void createPlaybookCreatesPolicy() {
    FakeAnsibleRepository repository = new FakeAnsibleRepository();
    repository.inventory = inventory("inv_1", "tenant_1", "prod");

    AnsibleResourceService service = new AnsibleResourceService(repository, new ObjectMapper());

    var response =
        service.createPlaybook(
            "tenant_1",
            new AnsiblePlaybookCreateRequest(
                "restart",
                "desc",
                "playbooks/restart.yml",
                null,
                Map.of(),
                List.of("restart"),
                List.of("inv_1"),
                List.of("service_name"),
                false,
                true,
                32768,
                1800,
                "alice"));

    assertEquals("restart", response.name());
    assertEquals(List.of("inv_1"), response.allowedInventoryIds());
    assertEquals(List.of("service_name"), response.allowedExtraVars());
  }

  @Test
  void createPlaybookRequiresAllowedInventory() {
    AnsibleResourceService service =
        new AnsibleResourceService(new FakeAnsibleRepository(), new ObjectMapper());

    assertThrows(
        AppException.class,
        () ->
            service.createPlaybook(
                "tenant_1",
                new AnsiblePlaybookCreateRequest(
                    "restart",
                    "desc",
                    "playbooks/restart.yml",
                    null,
                    Map.of(),
                    List.of("restart"),
                    List.of(),
                    List.of("service_name"),
                    false,
                    true,
                    32768,
                    1800,
                    "alice")));
  }

  @Test
  void createPlaybookRejectsDisabledAllowedInventory() {
    FakeAnsibleRepository repository = new FakeAnsibleRepository();
    repository.inventory = inventory("inv_1", "tenant_1", "prod");
    repository.inventoryEnabled = false;

    AnsibleResourceService service = new AnsibleResourceService(repository, new ObjectMapper());

    assertThrows(
        AppException.class,
        () ->
            service.createPlaybook(
                "tenant_1",
                new AnsiblePlaybookCreateRequest(
                    "restart",
                    "desc",
                    "playbooks/restart.yml",
                    null,
                    Map.of(),
                    List.of("restart"),
                    List.of("inv_1"),
                    List.of("service_name"),
                    false,
                    true,
                    32768,
                    1800,
                    "alice")));
  }

  private static AnsibleInventoryRecord inventory(String id, String tenantId, String name) {
    return new AnsibleInventoryRecord(
        id,
        tenantId,
        name,
        "desc",
        "inline",
        "[web]\n10.0.0.1",
        null,
        true,
        "alice",
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private static class FakeAnsibleRepository implements AnsibleRepository {
    AnsibleInventoryRecord inventory;
    boolean inventoryEnabled = true;
    AnsibleInventoryCreateCommand inventoryCommand;
    AnsiblePlaybookCreateCommand playbookCommand;
    AnsiblePolicyCreateCommand policyCommand;

    @Override
    public void createInventory(AnsibleInventoryCreateCommand command) {
      inventoryCommand = command;
      inventory =
          new AnsibleInventoryRecord(
              command.id(),
              command.tenantId(),
              command.name(),
              command.description(),
              command.inventoryType(),
              command.inlineInventory(),
              command.fileRef(),
              command.enabled(),
              command.createdBy(),
              OffsetDateTime.now(),
              OffsetDateTime.now());
    }

    @Override
    public List<AnsibleInventoryRecord> listInventories(String tenantId, boolean includeDisabled) {
      return inventory == null ? List.of() : List.of(inventory);
    }

    @Override
    public Optional<AnsibleInventoryRecord> findInventory(String tenantId, String inventoryId) {
      if (inventory != null && inventory.id().equals(inventoryId)) {
        inventory =
            new AnsibleInventoryRecord(
                inventory.id(),
                inventory.tenantId(),
                inventory.name(),
                inventory.description(),
                inventory.inventoryType(),
                inventory.inlineInventory(),
                inventory.fileRef(),
                inventoryEnabled,
                inventory.createdBy(),
                inventory.createdAt(),
                inventory.updatedAt());
        return Optional.of(inventory);
      }
      return Optional.empty();
    }

    @Override
    public boolean setInventoryEnabled(String tenantId, String inventoryId, boolean enabled) {
      return true;
    }

    @Override
    public void createPlaybook(AnsiblePlaybookCreateCommand command) {
      playbookCommand = command;
    }

    @Override
    public void createPolicy(AnsiblePolicyCreateCommand command) {
      policyCommand = command;
    }

    @Override
    public List<AnsiblePlaybookRecord> listPlaybooks(String tenantId, boolean includeDisabled) {
      return List.of();
    }

    @Override
    public Optional<AnsiblePlaybookRecord> findPlaybook(String tenantId, String playbookId) {
      if (playbookCommand == null) {
        return Optional.empty();
      }

      return Optional.of(
          new AnsiblePlaybookRecord(
              playbookCommand.id(),
              playbookCommand.tenantId(),
              playbookCommand.name(),
              playbookCommand.description(),
              playbookCommand.playbookRef(),
              playbookCommand.playbookContent(),
              playbookCommand.variablesSchemaJson(),
              playbookCommand.allowedTagsJson(),
              playbookCommand.enabled(),
              playbookCommand.createdBy(),
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public Optional<AnsiblePolicyRecord> findPolicy(String tenantId, String playbookId) {
      if (policyCommand == null) {
        return Optional.empty();
      }

      return Optional.of(
          new AnsiblePolicyRecord(
              policyCommand.id(),
              policyCommand.tenantId(),
              policyCommand.playbookId(),
              policyCommand.allowLive(),
              policyCommand.defaultCheckMode(),
              policyCommand.allowedInventoryIdsJson(),
              policyCommand.allowedExtraVarsJson(),
              policyCommand.maxExtraVarsBytes(),
              policyCommand.timeoutSeconds(),
              policyCommand.enabled(),
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public boolean setPlaybookEnabled(String tenantId, String playbookId, boolean enabled) {
      return true;
    }
  }
}

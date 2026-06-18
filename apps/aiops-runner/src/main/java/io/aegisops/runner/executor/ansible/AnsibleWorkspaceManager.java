package io.aegisops.runner.executor.ansible;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.AnsibleInventoryRecord;
import io.aegisops.execution.dto.AnsiblePlaybookRecord;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AnsibleWorkspaceManager {
  private final AnsibleRunnerProperties properties;

  public AnsibleWorkspaceManager(AnsibleRunnerProperties properties) {
    this.properties = properties;
  }

  public AnsibleWorkspace create(AnsibleInventoryRecord inventory, AnsiblePlaybookRecord playbook) {
    try {
      Path workspaceRoot = properties.getWorkspaceRoot().toAbsolutePath().normalize();
      Files.createDirectories(workspaceRoot);

      Path root =
          Files.createTempDirectory(
              workspaceRoot, "ansible-" + UUID.randomUUID().toString().replace("-", "") + "-");

      Path inventoryFile = root.resolve("inventory.ini").normalize();
      Path playbookFile = root.resolve("playbook.yml").normalize();

      ensureInside(root, inventoryFile);
      ensureInside(root, playbookFile);

      writeInventory(inventory, inventoryFile);
      writePlaybook(playbook, playbookFile);

      return new AnsibleWorkspace(root, inventoryFile, playbookFile);
    } catch (AppException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AppException(
          "ANSIBLE_WORKSPACE_CREATE_FAILED", "Failed to create Ansible workspace");
    }
  }

  public void cleanup(AnsibleWorkspace workspace) {
    if (workspace == null || !properties.isCleanupWorkspace()) {
      return;
    }

    try {
      deleteRecursively(workspace.root());
    } catch (Exception ignored) {
      // Cleanup failure should not hide execution result.
    }
  }

  private void writeInventory(AnsibleInventoryRecord inventory, Path inventoryFile)
      throws Exception {
    if ("inline".equals(inventory.inventoryType())) {
      writeText(inventoryFile, inventory.inlineInventory());
      return;
    }

    if ("file_ref".equals(inventory.inventoryType())) {
      copyResourceFile(inventory.fileRef(), inventoryFile);
      return;
    }

    throw new AppException("ANSIBLE_INVENTORY_TYPE_INVALID", "Unsupported inventory type");
  }

  private void writePlaybook(AnsiblePlaybookRecord playbook, Path playbookFile) throws Exception {
    if (playbook.playbookContent() != null && !playbook.playbookContent().isBlank()) {
      writeText(playbookFile, playbook.playbookContent());
      return;
    }

    if (playbook.playbookRef() != null && !playbook.playbookRef().isBlank()) {
      copyResourceFile(playbook.playbookRef(), playbookFile);
      return;
    }

    throw new AppException(
        "ANSIBLE_PLAYBOOK_SOURCE_REQUIRED", "Ansible playbook content or ref is required");
  }

  private void writeText(Path target, String content) throws Exception {
    String text = content == null ? "" : content;
    if (text.getBytes(StandardCharsets.UTF_8).length
        > properties.normalizedMaxMaterializedFileBytes()) {
      throw new AppException(
          "ANSIBLE_MATERIALIZED_FILE_TOO_LARGE", "Ansible materialized file is too large");
    }
    Files.writeString(target, text, StandardCharsets.UTF_8);
  }

  private void copyResourceFile(String ref, Path target) throws Exception {
    if (ref == null || ref.isBlank()) {
      throw new AppException("ANSIBLE_RESOURCE_REF_REQUIRED", "Ansible resource ref is required");
    }

    Path resourceRoot = properties.getResourceRoot().toAbsolutePath().normalize();
    Path source = resourceRoot.resolve(ref).normalize();

    if (!source.startsWith(resourceRoot)) {
      throw new AppException(
          "ANSIBLE_RESOURCE_REF_INVALID", "Ansible resource ref escapes resource root");
    }

    if (!Files.isRegularFile(source)) {
      throw new AppException("ANSIBLE_RESOURCE_NOT_FOUND", "Ansible resource file not found");
    }

    if (Files.size(source) > properties.normalizedMaxMaterializedFileBytes()) {
      throw new AppException(
          "ANSIBLE_MATERIALIZED_FILE_TOO_LARGE", "Ansible resource file is too large");
    }

    Files.copy(source, target);
  }

  private void ensureInside(Path root, Path child) {
    if (!child.startsWith(root)) {
      throw new AppException("ANSIBLE_WORKSPACE_PATH_INVALID", "Ansible workspace path is invalid");
    }
  }

  private void deleteRecursively(Path root) throws Exception {
    if (root == null || !Files.exists(root)) {
      return;
    }

    try (var stream = Files.walk(root)) {
      stream
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (Exception ignored) {
                  // best effort
                }
              });
    }
  }
}

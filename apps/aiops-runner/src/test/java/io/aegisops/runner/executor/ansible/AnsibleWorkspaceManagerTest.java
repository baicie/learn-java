package io.aegisops.runner.executor.ansible;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.common.exception.AppException;
import io.aegisops.execution.dto.AnsibleInventoryRecord;
import io.aegisops.execution.dto.AnsiblePlaybookRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnsibleWorkspaceManagerTest {
  @TempDir Path tempDir;

  @Test
  void createWorkspaceFromInlineResources() throws Exception {
    AnsibleRunnerProperties properties = new AnsibleRunnerProperties();
    properties.setWorkspaceRoot(tempDir.resolve("workspaces"));
    properties.setResourceRoot(tempDir.resolve("resources"));
    properties.setCleanupWorkspace(false);

    AnsibleWorkspaceManager manager =
        new AnsibleWorkspaceManager(properties, new AnsibleContentSafetyScanner());

    AnsibleWorkspace workspace = manager.create(inventoryInline(), playbookInline());

    assertTrue(Files.exists(workspace.inventoryFile()));
    assertTrue(Files.exists(workspace.playbookFile()));
    assertTrue(Files.readString(workspace.inventoryFile()).contains("[web]"));
    assertTrue(Files.readString(workspace.playbookFile()).contains("hosts: all"));
  }

  @Test
  void rejectResourceRefPathTraversal() {
    AnsibleRunnerProperties properties = new AnsibleRunnerProperties();
    properties.setWorkspaceRoot(tempDir.resolve("workspaces"));
    properties.setResourceRoot(tempDir.resolve("resources"));

    AnsibleWorkspaceManager manager =
        new AnsibleWorkspaceManager(properties, new AnsibleContentSafetyScanner());

    assertThrows(
        AppException.class,
        () -> manager.create(inventoryFileRef("../secret.ini"), playbookInline()));
  }

  @Test
  void rejectInlineInventoryWithCredentialLikeContent() {
    AnsibleRunnerProperties properties = new AnsibleRunnerProperties();
    properties.setWorkspaceRoot(tempDir.resolve("workspaces"));
    properties.setResourceRoot(tempDir.resolve("resources"));
    properties.setCleanupWorkspace(false);

    AnsibleWorkspaceManager manager =
        new AnsibleWorkspaceManager(properties, new AnsibleContentSafetyScanner());

    AnsibleInventoryRecord inventory =
        new AnsibleInventoryRecord(
            "inv_1",
            "tenant_1",
            "prod",
            "desc",
            "inline",
            "[web]\n10.0.0.1 ansible_password=123456",
            null,
            true,
            "alice",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    assertThrows(AppException.class, () -> manager.create(inventory, playbookInline()));
  }

  @Test
  void rejectInlinePlaybookWithCredentialLikeContent() {
    AnsibleRunnerProperties properties = new AnsibleRunnerProperties();
    properties.setWorkspaceRoot(tempDir.resolve("workspaces"));
    properties.setResourceRoot(tempDir.resolve("resources"));
    properties.setCleanupWorkspace(false);

    AnsibleWorkspaceManager manager =
        new AnsibleWorkspaceManager(properties, new AnsibleContentSafetyScanner());

    AnsiblePlaybookRecord playbook =
        new AnsiblePlaybookRecord(
            "pb_1",
            "tenant_1",
            "bad",
            "desc",
            null,
            """
            - hosts: all
              vars:
                ansible_ssh_private_key_file: /root/.ssh/id_rsa
              tasks:
                - debug:
                    msg: bad
            """,
            "{}",
            "[\"restart\"]",
            true,
            "alice",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    assertThrows(AppException.class, () -> manager.create(inventoryInline(), playbook));
  }

  private AnsibleInventoryRecord inventoryInline() {
    return new AnsibleInventoryRecord(
        "inv_1",
        "tenant_1",
        "prod",
        "desc",
        "inline",
        "[web]\n127.0.0.1 ansible_connection=local",
        null,
        true,
        "alice",
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private AnsibleInventoryRecord inventoryFileRef(String ref) {
    return new AnsibleInventoryRecord(
        "inv_1",
        "tenant_1",
        "prod",
        "desc",
        "file_ref",
        null,
        ref,
        true,
        "alice",
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private AnsiblePlaybookRecord playbookInline() {
    return new AnsiblePlaybookRecord(
        "pb_1",
        "tenant_1",
        "restart",
        "desc",
        null,
        """
        - hosts: all
          gather_facts: false
          tasks:
            - debug:
                msg: hello
        """,
        "{}",
        "[\"restart\"]",
        true,
        "alice",
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}

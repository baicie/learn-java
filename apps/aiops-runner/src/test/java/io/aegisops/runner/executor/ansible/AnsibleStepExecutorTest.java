package io.aegisops.runner.executor.ansible;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.AnsibleJson;
import io.aegisops.execution.AnsibleRepository;
import io.aegisops.execution.dto.AnsibleInventoryCreateCommand;
import io.aegisops.execution.dto.AnsibleInventoryRecord;
import io.aegisops.execution.dto.AnsiblePlaybookCreateCommand;
import io.aegisops.execution.dto.AnsiblePlaybookRecord;
import io.aegisops.execution.dto.AnsiblePolicyCreateCommand;
import io.aegisops.execution.dto.AnsiblePolicyRecord;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.runner.executor.StepExecutionContext;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnsibleStepExecutorTest {
  @TempDir Path tempDir;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final AnsibleJson json = new AnsibleJson(objectMapper);

  @Test
  void dryRunExecutesAnsibleCheckAndWritesArtifact() {
    FakeAnsibleProcessRunner processRunner =
        new FakeAnsibleProcessRunner(new AnsibleProcessResult(0, false, 12, "ok", ""));

    AnsibleStepExecutor executor = executor(new FakeAnsibleRepository(), processRunner, true);

    var result = executor.execute(context("dry_run", false), step(payload()));

    assertTrue(result.success());
    assertTrue(processRunner.called);
    assertTrue(processRunner.argv.contains("--check"));
    assertEquals(1, result.artifacts().size());
    assertEquals("ansible-check-result.json", result.artifacts().get(0).name());
    assertTrue(result.artifacts().get(0).content().contains("\"executed\":true"));
  }

  @Test
  void dryRunReturnsFailureWhenAnsibleCheckFails() {
    FakeAnsibleProcessRunner processRunner =
        new FakeAnsibleProcessRunner(new AnsibleProcessResult(2, false, 12, "", "bad playbook"));

    AnsibleStepExecutor executor = executor(new FakeAnsibleRepository(), processRunner, true);

    var result = executor.execute(context("dry_run", false), step(payload()));

    assertFalse(result.success());
    assertTrue(result.errorMessage().contains("exit code 2"));
    assertEquals(1, result.artifacts().size());
  }

  @Test
  void checkExecutionDisabledFallsBackToPreviewOnly() {
    FakeAnsibleProcessRunner processRunner =
        new FakeAnsibleProcessRunner(new AnsibleProcessResult(0, false, 12, "ok", ""));

    AnsibleStepExecutor executor = executor(new FakeAnsibleRepository(), processRunner, false);

    var result = executor.execute(context("dry_run", false), step(payload()));

    assertTrue(result.success());
    assertFalse(processRunner.called);
    assertEquals("ansible-dry-run-preview.json", result.artifacts().get(0).name());
  }

  @Test
  void liveDisabledFailsWithoutExecution() {
    FakeAnsibleProcessRunner processRunner =
        new FakeAnsibleProcessRunner(new AnsibleProcessResult(0, false, 12, "ok", ""));

    AnsibleStepExecutor executor = executor(new FakeAnsibleRepository(), processRunner, true);

    var result = executor.execute(context("live", false), step(payload()));

    assertFalse(result.success());
    assertFalse(processRunner.called);
    assertEquals("Live Ansible execution is disabled.", result.errorMessage());
  }

  private AnsibleStepExecutor executor(
      FakeAnsibleRepository repository,
      FakeAnsibleProcessRunner processRunner,
      boolean checkExecutionEnabled) {
    AnsibleRunnerProperties properties = new AnsibleRunnerProperties();
    properties.setCheckExecutionEnabled(checkExecutionEnabled);
    properties.setWorkspaceRoot(tempDir.resolve("workspaces"));
    properties.setResourceRoot(tempDir.resolve("resources"));
    properties.setCleanupWorkspace(true);
    properties.setBinary("ansible-playbook");

    AnsibleWorkspaceManager workspaceManager =
        new AnsibleWorkspaceManager(properties, new AnsibleContentSafetyScanner());

    return new AnsibleStepExecutor(
        AnsibleStepExecutorDeps.create(
            new AnsibleSupport(
                repository,
                new AnsibleSafetyValidator(objectMapper),
                new AnsibleCommandPreviewBuilder(objectMapper),
                workspaceManager,
                processRunner),
            new AnsibleRuntime(objectMapper, new AnsibleJson(objectMapper)),
            properties));
  }

  private StepExecutionContext context(String mode, boolean liveEnabled) {
    return new StepExecutionContext(
        new ExecutionRunRecord(
            "exec_1",
            "tenant_1",
            "inc_1",
            "plan_1",
            "running",
            mode,
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
            OffsetDateTime.now(),
            OffsetDateTime.now()),
        liveEnabled);
  }

  private ExecutionStepRecord step(String payload) {
    return new ExecutionStepRecord(
        "step_1",
        "tenant_1",
        "exec_1",
        "planstep_1",
        1,
        "Restart service",
        "ansible",
        "service",
        "queued",
        payload,
        "",
        null,
        null,
        null,
        null,
        1,
        300,
        0,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private String payload() {
    return json.write(
        Map.of(
            "inventoryId",
            "inv_1",
            "playbookId",
            "pb_1",
            "checkMode",
            true,
            "tags",
            List.of("restart"),
            "extraVars",
            Map.of("service_name", "order-service")));
  }

  private class FakeAnsibleRepository implements AnsibleRepository {
    @Override
    public Optional<AnsibleInventoryRecord> findInventory(String tenantId, String inventoryId) {
      return Optional.of(
          new AnsibleInventoryRecord(
              "inv_1",
              tenantId,
              "prod",
              "desc",
              "inline",
              "[web]\n127.0.0.1 ansible_connection=local",
              null,
              true,
              "alice",
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public Optional<AnsiblePlaybookRecord> findPlaybook(String tenantId, String playbookId) {
      return Optional.of(
          new AnsiblePlaybookRecord(
              "pb_1",
              tenantId,
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
              json.write(List.of("restart")),
              true,
              "alice",
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public Optional<AnsiblePolicyRecord> findPolicy(String tenantId, String playbookId) {
      return Optional.of(
          new AnsiblePolicyRecord(
              "apol_1",
              tenantId,
              playbookId,
              false,
              true,
              true,
              json.write(List.of("inv_1")),
              json.write(List.of("service_name")),
              32768,
              1800,
              true,
              OffsetDateTime.now(),
              OffsetDateTime.now()));
    }

    @Override
    public void createInventory(AnsibleInventoryCreateCommand command) {}

    @Override
    public List<AnsibleInventoryRecord> listInventories(String tenantId, boolean includeDisabled) {
      return List.of();
    }

    @Override
    public boolean setInventoryEnabled(String tenantId, String inventoryId, boolean enabled) {
      return true;
    }

    @Override
    public void createPlaybook(AnsiblePlaybookCreateCommand command) {}

    @Override
    public void createPolicy(AnsiblePolicyCreateCommand command) {}

    @Override
    public List<AnsiblePlaybookRecord> listPlaybooks(String tenantId, boolean includeDisabled) {
      return List.of();
    }

    @Override
    public boolean setPlaybookEnabled(String tenantId, String playbookId, boolean enabled) {
      return true;
    }
  }

  private static class FakeAnsibleProcessRunner implements AnsibleProcessRunner {
    final AnsibleProcessResult result;
    boolean called;
    List<String> argv;

    FakeAnsibleProcessRunner(AnsibleProcessResult result) {
      this.result = result;
    }

    @Override
    public AnsibleProcessResult run(List<String> argv, Path workingDirectory, Duration timeout) {
      this.called = true;
      this.argv = argv;
      return result;
    }
  }
}

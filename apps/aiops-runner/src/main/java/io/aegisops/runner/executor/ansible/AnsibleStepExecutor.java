package io.aegisops.runner.executor.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.AnsibleJson;
import io.aegisops.execution.AnsibleRepository;
import io.aegisops.execution.dto.AnsibleInventoryRecord;
import io.aegisops.execution.dto.AnsiblePlaybookRecord;
import io.aegisops.execution.dto.AnsiblePolicyRecord;
import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.runner.executor.StepExecutionContext;
import io.aegisops.runner.executor.StepExecutionResult;
import io.aegisops.runner.executor.StepExecutor;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AnsibleStepExecutor implements StepExecutor {
  private final AnsibleRepository repository;
  private final AnsibleSafetyValidator validator;
  private final AnsibleCommandPreviewBuilder commandBuilder;
  private final AnsibleWorkspaceManager workspaceManager;
  private final AnsibleProcessRunner processRunner;
  private final AnsibleRunnerProperties properties;
  private final ObjectMapper objectMapper;
  private final AnsibleJson json;

  public AnsibleStepExecutor(AnsibleStepExecutorDeps deps) {
    this.repository = deps.repository();
    this.validator = deps.validator();
    this.commandBuilder = deps.commandBuilder();
    this.workspaceManager = deps.workspaceManager();
    this.processRunner = deps.processRunner();
    this.properties = deps.properties();
    this.objectMapper = deps.objectMapper();
    this.json = deps.json();
  }

  @Override
  public boolean supports(String actionType) {
    return "ansible".equals(actionType);
  }

  @Override
  public StepExecutionResult execute(StepExecutionContext context, ExecutionStepRecord step) {
    AnsibleActionPayload payload =
        AnsibleActionPayload.parse(objectMapper, step.actionPayloadJson());

    AnsibleInventoryRecord inventory =
        repository
            .findInventory(step.tenantId(), payload.inventoryId())
            .orElseThrow(
                () ->
                    new AppException("ANSIBLE_INVENTORY_NOT_FOUND", "Ansible inventory not found"));

    AnsiblePlaybookRecord playbook =
        repository
            .findPlaybook(step.tenantId(), payload.playbookId())
            .orElseThrow(
                () -> new AppException("ANSIBLE_PLAYBOOK_NOT_FOUND", "Ansible playbook not found"));

    AnsiblePolicyRecord policy =
        repository
            .findPolicy(step.tenantId(), playbook.id())
            .orElseThrow(
                () ->
                    new AppException(
                        "ANSIBLE_POLICY_NOT_FOUND", "Ansible execution policy not found"));

    if (!context.dryRun()) {
      if (!context.liveEnabled()) {
        return StepExecutionResult.failure(
            "Live Ansible execution is disabled.",
            List.of(
                artifact(
                    step,
                    "ansible-live-disabled.json",
                    writeArtifact(
                        mapOf(
                            "inventoryId", inventory.id(),
                            "playbookId", playbook.id(),
                            "live", false)))));
      }

      validator.validateLive(inventory, playbook, policy, payload);
    }

    if (!properties.isCheckExecutionEnabled()) {
      validator.validateDryRunPreview(inventory, playbook, policy, payload);
      return previewOnly(step, inventory, playbook, payload);
    }

    validator.validateCheckExecution(inventory, playbook, policy, payload);
    return executeCheck(step, inventory, playbook, policy, payload);
  }

  private StepExecutionResult previewOnly(
      ExecutionStepRecord step,
      AnsibleInventoryRecord inventory,
      AnsiblePlaybookRecord playbook,
      AnsibleActionPayload payload) {
    AnsibleWorkspace workspace = workspaceManager.create(inventory, playbook);
    try {
      List<String> argv =
          commandBuilder.buildArgv(
              properties.getBinary(),
              workspace.inventoryFile(),
              workspace.playbookFile(),
              payload,
              true);

      String displayCommand = commandBuilder.toDisplayCommand(argv);

      return StepExecutionResult.success(
          "DRY-RUN Ansible playbook preview generated.",
          List.of(
              artifact(
                  step,
                  "ansible-dry-run-preview.json",
                  writeArtifact(
                      mapOf(
                          "commandPreview",
                          displayCommand,
                          "argv",
                          argv,
                          "inventoryId",
                          inventory.id(),
                          "playbookId",
                          playbook.id(),
                          "checkMode",
                          true,
                          "executed",
                          false)))));
    } finally {
      workspaceManager.cleanup(workspace);
    }
  }

  private StepExecutionResult executeCheck(
      ExecutionStepRecord step,
      AnsibleInventoryRecord inventory,
      AnsiblePlaybookRecord playbook,
      AnsiblePolicyRecord policy,
      AnsibleActionPayload payload) {
    AnsibleWorkspace workspace = workspaceManager.create(inventory, playbook);
    try {
      List<String> argv =
          commandBuilder.buildArgv(
              properties.getBinary(),
              workspace.inventoryFile(),
              workspace.playbookFile(),
              payload,
              true);

      AnsibleProcessResult result =
          processRunner.run(argv, workspace.root(), Duration.ofSeconds(policy.timeoutSeconds()));

      String displayCommand = commandBuilder.toDisplayCommand(argv);

      ExecutionArtifactCreateCommand artifact =
          artifact(
              step,
              "ansible-check-result.json",
              writeArtifact(
                  mapOf(
                      "commandPreview", displayCommand,
                      "argv", argv,
                      "inventoryId", inventory.id(),
                      "playbookId", playbook.id(),
                      "checkMode", true,
                      "executed", true,
                      "exitCode", result.exitCode(),
                      "timedOut", result.timedOut(),
                      "durationMillis", result.durationMillis(),
                      "stdout", result.stdout(),
                      "stderr", result.stderr())));

      if (result.success()) {
        return StepExecutionResult.success("Ansible check execution succeeded.", List.of(artifact));
      }

      return StepExecutionResult.failure(
          result.timedOut()
              ? "Ansible check execution timed out."
              : "Ansible check execution failed with exit code " + result.exitCode(),
          List.of(artifact));
    } finally {
      workspaceManager.cleanup(workspace);
    }
  }

  private ExecutionArtifactCreateCommand artifact(
      ExecutionStepRecord step, String name, String content) {
    return new ExecutionArtifactCreateCommand(
        newId("artifact"),
        step.tenantId(),
        step.executionId(),
        step.id(),
        "json",
        name,
        content,
        "{}");
  }

  private String writeArtifact(Map<String, Object> data) {
    return json.write(data);
  }

  private static Map<String, Object> mapOf(Object... keyValues) {
    if ((keyValues.length & 1) != 0) {
      throw new IllegalArgumentException("mapOf requires an even number of arguments");
    }
    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put((String) keyValues[i], keyValues[i + 1]);
    }
    return map;
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}

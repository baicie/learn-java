package io.aegisops.runner.executor.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.execution.AnsibleJson;
import io.aegisops.execution.AnsibleRepository;
import io.aegisops.execution.ExecutionRepository;
import io.aegisops.execution.dto.AnsibleCredentialRecord;
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
  private final ExecutionRepository executionRepository;
  private final AnsibleSafetyValidator validator;
  private final AnsibleCommandPreviewBuilder commandBuilder;
  private final AnsibleWorkspaceManager workspaceManager;
  private final AnsibleProcessRunner processRunner;
  private final AnsibleOutputMasker outputMasker;
  private final AnsibleRunnerProperties properties;
  private final ObjectMapper objectMapper;
  private final AnsibleJson json;

  public AnsibleStepExecutor(AnsibleStepExecutorDeps deps) {
    this.repository = deps.repository();
    this.executionRepository = deps.executionRepository();
    this.validator = deps.validator();
    this.commandBuilder = deps.commandBuilder();
    this.workspaceManager = deps.workspaceManager();
    this.processRunner = deps.processRunner();
    this.outputMasker = deps.outputMasker();
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

    if (context.dryRun()) {
      return executeCheck(context, step, inventory, playbook, policy, payload);
    }

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

    return executeLive(context, step, inventory, playbook, policy, payload);
  }

  private StepExecutionResult executeCheck(
      StepExecutionContext context,
      ExecutionStepRecord step,
      AnsibleInventoryRecord inventory,
      AnsiblePlaybookRecord playbook,
      AnsiblePolicyRecord policy,
      AnsibleActionPayload payload) {
    if (!properties.isCheckExecutionEnabled()) {
      validator.validateDryRunPreview(inventory, playbook, policy, payload);
      return previewOnly(step, inventory, playbook, payload);
    }

    validator.validateCheckExecution(inventory, playbook, policy, payload);
    return executeProcess(step, inventory, playbook, policy, payload, true, false);
  }

  private StepExecutionResult executeLive(
      StepExecutionContext context,
      ExecutionStepRecord step,
      AnsibleInventoryRecord inventory,
      AnsiblePlaybookRecord playbook,
      AnsiblePolicyRecord policy,
      AnsibleActionPayload payload) {
    AnsibleCredentialRecord credential = null;
    if (payload.credentialRefId() != null && !payload.credentialRefId().isBlank()) {
      credential =
          repository.findCredential(step.tenantId(), payload.credentialRefId()).orElse(null);
    }

    validator.validateLive(
        inventory, playbook, policy, payload, context.run(), credential);

    boolean marked =
        executionRepository.markLiveGuardPassed(context.run().tenantId(), context.run().id());
    if (!marked) {
      throw new AppException(
          "ANSIBLE_LIVE_GUARD_UPDATE_FAILED", "Failed to mark Ansible live guard passed");
    }

    return executeProcess(step, inventory, playbook, policy, payload, false, true);
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
                          "commandPreview", displayCommand,
                          "argv", argv,
                          "inventoryId", inventory.id(),
                          "playbookId", playbook.id(),
                          "checkMode", true,
                          "executed", false)))));
    } finally {
      workspaceManager.cleanup(workspace);
    }
  }

  private StepExecutionResult executeProcess(
      ExecutionStepRecord step,
      AnsibleInventoryRecord inventory,
      AnsiblePlaybookRecord playbook,
      AnsiblePolicyRecord policy,
      AnsibleActionPayload payload,
      boolean checkMode,
      boolean live) {
    AnsibleWorkspace workspace = workspaceManager.create(inventory, playbook);
    try {
      List<String> argv =
          commandBuilder.buildArgv(
              properties.getBinary(),
              workspace.inventoryFile(),
              workspace.playbookFile(),
              payload,
              checkMode);

      int timeoutSeconds = live ? policy.liveTimeoutSeconds() : policy.timeoutSeconds();

      AnsibleProcessResult result =
          processRunner.run(
              argv, workspace.root(), Duration.ofSeconds(timeoutSeconds), checkMode);

      String displayCommand = commandBuilder.toDisplayCommand(argv);

      String stdout =
          policy.stdoutStderrMaskingEnabled()
              ? outputMasker.mask(result.stdout())
              : result.stdout();
      String stderr =
          policy.stdoutStderrMaskingEnabled()
              ? outputMasker.mask(result.stderr())
              : result.stderr();

      ExecutionArtifactCreateCommand artifactCmd =
          artifact(
              step,
              live ? "ansible-live-result.json" : "ansible-check-result.json",
              writeArtifact(
                  mapOf(
                      "commandPreview", displayCommand,
                      "argv", argv,
                      "inventoryId", inventory.id(),
                      "playbookId", playbook.id(),
                      "checkMode", checkMode,
                      "live", live,
                      "executed", true,
                      "exitCode", result.exitCode(),
                      "timedOut", result.timedOut(),
                      "durationMillis", result.durationMillis(),
                      "stdout", stdout,
                      "stderr", stderr)));

      if (result.success()) {
        return StepExecutionResult.success(
            live ? "Ansible live execution succeeded." : "Ansible check execution succeeded.",
            List.of(artifactCmd));
      }

      return StepExecutionResult.failure(
          result.timedOut()
              ? (live
                      ? "Ansible live execution timed out."
                      : "Ansible check execution timed out.")
              : (live
                      ? "Ansible live execution failed with exit code " + result.exitCode()
                      : "Ansible check execution failed with exit code " + result.exitCode()),
          List.of(artifactCmd));
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

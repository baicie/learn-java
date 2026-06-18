package io.aegisops.runner.executor.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AnsibleStepExecutor implements StepExecutor {
  private final AnsibleRepository repository;
  private final AnsibleSafetyValidator validator;
  private final AnsibleCommandPreviewBuilder previewBuilder;
  private final ObjectMapper objectMapper;
  private final AnsibleJson json;

  public AnsibleStepExecutor(
      AnsibleRepository repository,
      AnsibleSafetyValidator validator,
      AnsibleCommandPreviewBuilder previewBuilder,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.validator = validator;
    this.previewBuilder = previewBuilder;
    this.objectMapper = objectMapper;
    this.json = new AnsibleJson(objectMapper);
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
                    new io.aegisops.common.exception.AppException(
                        "ANSIBLE_INVENTORY_NOT_FOUND", "Ansible inventory not found"));

    AnsiblePlaybookRecord playbook =
        repository
            .findPlaybook(step.tenantId(), payload.playbookId())
            .orElseThrow(
                () ->
                    new io.aegisops.common.exception.AppException(
                        "ANSIBLE_PLAYBOOK_NOT_FOUND", "Ansible playbook not found"));

    AnsiblePolicyRecord policy =
        repository
            .findPolicy(step.tenantId(), playbook.id())
            .orElseThrow(
                () ->
                    new io.aegisops.common.exception.AppException(
                        "ANSIBLE_POLICY_NOT_FOUND", "Ansible execution policy not found"));

    boolean checkMode = context.dryRun() || payload.checkMode() == null || payload.checkMode();

    if (context.dryRun()) {
      validator.validateDryRun(inventory, playbook, policy, payload);
      String preview = previewBuilder.build(inventory, playbook, payload, true);

      return StepExecutionResult.success(
          "DRY-RUN Ansible playbook preview generated.",
          List.of(
              artifact(
                  step,
                  "ansible-dry-run-preview.json",
                  json.write(
                      Map.of(
                          "commandPreview",
                          preview,
                          "inventoryId",
                          inventory.id(),
                          "playbookId",
                          playbook.id(),
                          "checkMode",
                          true,
                          "live",
                          false)))));
    }

    if (!context.liveEnabled()) {
      return StepExecutionResult.failure(
          "Live Ansible execution is disabled.",
          List.of(
              artifact(
                  step,
                  "ansible-live-disabled.json",
                  json.write(
                      Map.of(
                          "inventoryId", inventory.id(),
                          "playbookId", playbook.id(),
                          "live", false)))));
    }

    validator.validateLive(inventory, playbook, policy, payload);

    return StepExecutionResult.failure(
        "Live Ansible execution is not implemented in Phase5.5. Use dry-run mode.",
        List.of(
            artifact(
                step,
                "ansible-live-not-implemented.json",
                json.write(
                    Map.of(
                        "inventoryId", inventory.id(),
                        "playbookId", playbook.id(),
                        "checkMode", checkMode)))));
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

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}

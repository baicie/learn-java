package io.aegisops.runner.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.ExecutionJson;
import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ShellDryRunStepExecutor implements StepExecutor {
  private final ExecutionJson json;

  public ShellDryRunStepExecutor(ObjectMapper objectMapper) {
    this.json = new ExecutionJson(objectMapper);
  }

  @Override
  public boolean supports(String actionType) {
    return "shell".equals(actionType);
  }

  @Override
  public StepExecutionResult execute(StepExecutionContext context, ExecutionStepRecord step) {
    String command = json.textValue(step.actionPayloadJson(), "command");

    if (context.dryRun()) {
      return StepExecutionResult.success(
          "DRY-RUN shell command: " + command,
          List.of(
              new ExecutionArtifactCreateCommand(
                  newId("artifact"),
                  step.tenantId(),
                  step.executionId(),
                  step.id(),
                  "text",
                  "dry-run-command.txt",
                  command,
                  json.write(Map.of("actionType", "shell", "dryRun", true)))));
    }

    if (!context.liveEnabled()) {
      return StepExecutionResult.failure("Live shell execution is disabled.");
    }

    return StepExecutionResult.failure(
        "Live shell execution is not implemented in Phase5.3. Use dry-run mode.");
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}

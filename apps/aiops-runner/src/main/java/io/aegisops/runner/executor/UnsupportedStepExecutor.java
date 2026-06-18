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
public class UnsupportedStepExecutor implements StepExecutor {
  private final ExecutionJson json;

  public UnsupportedStepExecutor(ObjectMapper objectMapper) {
    this.json = new ExecutionJson(objectMapper);
  }

  @Override
  public boolean supports(String actionType) {
    return true;
  }

  @Override
  public StepExecutionResult execute(StepExecutionContext context, ExecutionStepRecord step) {
    String message = "Unsupported action type in Phase5.3: " + step.actionType();

    return StepExecutionResult.failure(
        message,
        List.of(
            new ExecutionArtifactCreateCommand(
                newId("artifact"),
                step.tenantId(),
                step.executionId(),
                step.id(),
                "text",
                "unsupported-action.txt",
                message,
                json.write(Map.of("actionType", step.actionType())))));
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}

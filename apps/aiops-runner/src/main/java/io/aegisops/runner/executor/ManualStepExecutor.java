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
public class ManualStepExecutor implements StepExecutor {
  private final ExecutionJson json;

  public ManualStepExecutor(ObjectMapper objectMapper) {
    this.json = new ExecutionJson(objectMapper);
  }

  @Override
  public boolean supports(String actionType) {
    return "manual".equals(actionType);
  }

  @Override
  public StepExecutionResult execute(StepExecutionContext context, ExecutionStepRecord step) {
    return StepExecutionResult.success(
        "Manual step recorded by runner. No external action was executed.",
        List.of(
            new ExecutionArtifactCreateCommand(
                newId("artifact"),
                step.tenantId(),
                step.executionId(),
                step.id(),
                "text",
                "manual-step.txt",
                "Manual step recorded. No external action was executed.",
                json.write(Map.of("actionType", step.actionType(), "dryRun", context.dryRun())))));
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}

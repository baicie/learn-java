package io.aegisops.runner.executor;

import io.aegisops.execution.dto.ExecutionStepRecord;
import org.springframework.stereotype.Component;

@Component
public class ManualStepExecutor implements StepExecutor {
  @Override
  public boolean supports(String actionType) {
    return "manual".equals(actionType);
  }

  @Override
  public StepExecutionResult execute(StepExecutionContext context, ExecutionStepRecord step) {
    return StepExecutionResult.success(
        "Manual step recorded by runner. No external action was executed.");
  }
}

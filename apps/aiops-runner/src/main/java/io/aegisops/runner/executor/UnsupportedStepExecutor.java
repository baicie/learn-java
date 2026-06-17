package io.aegisops.runner.executor;

import io.aegisops.execution.dto.ExecutionStepRecord;
import org.springframework.stereotype.Component;

@Component
public class UnsupportedStepExecutor implements StepExecutor {
  @Override
  public boolean supports(String actionType) {
    return true;
  }

  @Override
  public StepExecutionResult execute(StepExecutionContext context, ExecutionStepRecord step) {
    return StepExecutionResult.failure("Unsupported action type in Phase5.2: " + step.actionType());
  }
}

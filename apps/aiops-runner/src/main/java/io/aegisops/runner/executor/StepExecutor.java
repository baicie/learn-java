package io.aegisops.runner.executor;

import io.aegisops.execution.dto.ExecutionStepRecord;

public interface StepExecutor {
  boolean supports(String actionType);

  StepExecutionResult execute(StepExecutionContext context, ExecutionStepRecord step);
}

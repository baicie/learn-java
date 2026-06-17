package io.aegisops.runner.executor;

public record StepExecutionResult(boolean success, String output, String errorMessage) {
  public static StepExecutionResult success(String output) {
    return new StepExecutionResult(true, output, null);
  }

  public static StepExecutionResult failure(String errorMessage) {
    return new StepExecutionResult(false, null, errorMessage);
  }
}

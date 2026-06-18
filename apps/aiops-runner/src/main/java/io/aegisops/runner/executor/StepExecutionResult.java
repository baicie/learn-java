package io.aegisops.runner.executor;

import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import java.util.List;

public record StepExecutionResult(
    boolean success,
    String output,
    String errorMessage,
    List<ExecutionArtifactCreateCommand> artifacts) {
  public static StepExecutionResult success(String output) {
    return new StepExecutionResult(true, output, null, List.of());
  }

  public static StepExecutionResult success(
      String output, List<ExecutionArtifactCreateCommand> artifacts) {
    return new StepExecutionResult(true, output, null, artifacts == null ? List.of() : artifacts);
  }

  public static StepExecutionResult failure(String errorMessage) {
    return new StepExecutionResult(false, null, errorMessage, List.of());
  }

  public static StepExecutionResult failure(
      String errorMessage, List<ExecutionArtifactCreateCommand> artifacts) {
    return new StepExecutionResult(
        false, null, errorMessage, artifacts == null ? List.of() : artifacts);
  }
}

package io.aegisops.runner.executor.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.AnsibleJson;
import io.aegisops.execution.AnsibleRepository;
import io.aegisops.execution.ExecutionRepository;

/** Bundle of ansible collaborators that the step executor needs. */
public record AnsibleStepExecutorDeps(
    AnsibleSupport support,
    AnsibleRuntime runtime,
    AnsibleRunnerProperties properties,
    ExecutionRepository executionRepository,
    AnsibleOutputMasker outputMasker) {
  public static AnsibleStepExecutorDeps create(
      AnsibleSupport support,
      AnsibleRuntime runtime,
      AnsibleRunnerProperties properties,
      ExecutionRepository executionRepository,
      AnsibleOutputMasker outputMasker) {
    return new AnsibleStepExecutorDeps(
        support, runtime, properties, executionRepository, outputMasker);
  }

  public AnsibleRepository repository() {
    return support.repository();
  }

  public AnsibleSafetyValidator validator() {
    return support.validator();
  }

  public AnsibleCommandPreviewBuilder commandBuilder() {
    return support.commandBuilder();
  }

  public AnsibleWorkspaceManager workspaceManager() {
    return support.workspaceManager();
  }

  public AnsibleProcessRunner processRunner() {
    return support.processRunner();
  }

  public ObjectMapper objectMapper() {
    return runtime.objectMapper();
  }

  public AnsibleJson json() {
    return runtime.json();
  }
}

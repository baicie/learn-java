package io.aegisops.runner.executor.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.AnsibleJson;
import io.aegisops.execution.AnsibleRepository;

/** Bundle of ansible collaborators that the step executor needs. */
public record AnsibleStepExecutorDeps(
    AnsibleSupport support, AnsibleRuntime runtime, AnsibleRunnerProperties properties) {
  public static AnsibleStepExecutorDeps create(
      AnsibleSupport support, AnsibleRuntime runtime, AnsibleRunnerProperties properties) {
    return new AnsibleStepExecutorDeps(support, runtime, properties);
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

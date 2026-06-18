package io.aegisops.runner.executor.ansible;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.AnsibleJson;
import io.aegisops.execution.AnsibleRepository;
import io.aegisops.execution.ExecutionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires {@link AnsibleStepExecutor}'s collaborators into a single dependency object. */
@Configuration
public class AnsibleStepExecutorConfig {
  @Bean
  public AnsibleRuntime ansibleRuntime(ObjectMapper objectMapper) {
    return new AnsibleRuntime(objectMapper, new AnsibleJson(objectMapper));
  }

  @Bean
  public AnsibleSupport ansibleSupport(
      AnsibleRepository repository,
      AnsibleSafetyValidator validator,
      AnsibleCommandPreviewBuilder commandBuilder,
      AnsibleWorkspaceManager workspaceManager,
      AnsibleProcessRunner processRunner) {
    return new AnsibleSupport(
        repository, validator, commandBuilder, workspaceManager, processRunner);
  }

  @Bean
  public AnsibleStepExecutorDeps ansibleStepExecutorDeps(
      AnsibleSupport support,
      AnsibleRuntime runtime,
      AnsibleRunnerProperties properties,
      ExecutionRepository executionRepository,
      AnsibleOutputMasker outputMasker) {
    return new AnsibleStepExecutorDeps(
        support, runtime, properties, executionRepository, outputMasker);
  }
}

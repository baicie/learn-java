package io.aegisops.runner.executor.ansible;

import io.aegisops.execution.AnsibleRepository;

/** Step executor collaborators (repository, helpers, runner). */
public record AnsibleSupport(
    AnsibleRepository repository,
    AnsibleSafetyValidator validator,
    AnsibleCommandPreviewBuilder commandBuilder,
    AnsibleWorkspaceManager workspaceManager,
    AnsibleProcessRunner processRunner) {}

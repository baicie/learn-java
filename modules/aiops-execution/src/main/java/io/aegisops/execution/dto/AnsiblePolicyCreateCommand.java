package io.aegisops.execution.dto;

public record AnsiblePolicyCreateCommand(
    String id,
    String tenantId,
    String playbookId,
    boolean allowLive,
    boolean allowCheckExecution,
    boolean liveRequiresApproval,
    boolean defaultCheckMode,
    String allowedInventoryIdsJson,
    String allowedExtraVarsJson,
    String allowedLiveRiskLevelsJson,
    String allowedCredentialRefIdsJson,
    boolean stdoutStderrMaskingEnabled,
    int maxExtraVarsBytes,
    int timeoutSeconds,
    int liveTimeoutSeconds,
    boolean enabled) {}

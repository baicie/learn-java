package io.aegisops.execution.dto;

public record AnsiblePolicyCreateCommand(
    String id,
    String tenantId,
    String playbookId,
    boolean allowLive,
    boolean allowCheckExecution,
    boolean defaultCheckMode,
    String allowedInventoryIdsJson,
    String allowedExtraVarsJson,
    int maxExtraVarsBytes,
    int timeoutSeconds,
    boolean enabled) {}

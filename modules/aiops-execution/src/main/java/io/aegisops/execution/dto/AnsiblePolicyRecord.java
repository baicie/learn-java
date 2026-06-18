package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AnsiblePolicyRecord(
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
    boolean enabled,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

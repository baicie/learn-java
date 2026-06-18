package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AnsiblePolicyRecord(
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
    boolean enabled,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

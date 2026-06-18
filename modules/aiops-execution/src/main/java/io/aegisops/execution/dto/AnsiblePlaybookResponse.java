package io.aegisops.execution.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record AnsiblePlaybookResponse(
    String id,
    String name,
    String description,
    String playbookRef,
    String playbookContent,
    Map<String, Object> variablesSchema,
    List<String> allowedTags,
    boolean enabled,
    boolean allowLive,
    boolean allowCheckExecution,
    boolean liveRequiresApproval,
    boolean defaultCheckMode,
    List<String> allowedInventoryIds,
    List<String> allowedExtraVars,
    List<String> allowedLiveRiskLevels,
    List<String> allowedCredentialRefIds,
    boolean stdoutStderrMaskingEnabled,
    int maxExtraVarsBytes,
    int timeoutSeconds,
    int liveTimeoutSeconds,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

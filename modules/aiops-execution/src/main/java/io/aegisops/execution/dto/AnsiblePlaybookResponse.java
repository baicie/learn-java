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
    boolean defaultCheckMode,
    List<String> allowedInventoryIds,
    List<String> allowedExtraVars,
    int maxExtraVarsBytes,
    int timeoutSeconds,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

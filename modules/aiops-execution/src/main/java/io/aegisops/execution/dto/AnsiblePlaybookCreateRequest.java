package io.aegisops.execution.dto;

import java.util.List;
import java.util.Map;

public record AnsiblePlaybookCreateRequest(
    String name,
    String description,
    String playbookRef,
    String playbookContent,
    Map<String, Object> variablesSchema,
    List<String> allowedTags,
    List<String> allowedInventoryIds,
    List<String> allowedExtraVars,
    Boolean allowLive,
    Boolean allowCheckExecution,
    Boolean defaultCheckMode,
    Integer maxExtraVarsBytes,
    Integer timeoutSeconds,
    String createdBy) {}

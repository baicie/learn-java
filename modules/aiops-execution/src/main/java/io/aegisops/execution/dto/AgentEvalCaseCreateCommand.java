package io.aegisops.execution.dto;

public record AgentEvalCaseCreateCommand(
    String id,
    String tenantId,
    String datasetId,
    String sourceType,
    String sourceId,
    String incidentId,
    String title,
    String severity,
    String inputContext,
    String expectedRootCause,
    String expectedKeywordsJson,
    String expectedActionsJson,
    String forbiddenActionsJson,
    String tagsJson,
    boolean enabled,
    String createdBy) {}

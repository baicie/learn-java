package io.aegisops.runbook.dto;

public record RunbookStepTemplateRecord(
    String id,
    String runbookId,
    int sequenceNo,
    String name,
    String actionType,
    String targetType,
    String commandTemplate,
    String description,
    String expectedResult,
    String rollbackHint,
    boolean requiresApproval,
    int timeoutSeconds,
    String metadataJson) {}

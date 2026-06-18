package io.aegisops.execution.dto;

public record ExecutionStepCreateCommand(
    String id,
    String tenantId,
    String executionId,
    String planStepId,
    int sequenceNo,
    String name,
    String actionType,
    String targetType,
    String status,
    String actionPayloadJson,
    String commandSnapshot,
    int attempt,
    int timeoutSeconds) {}

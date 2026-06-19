package io.aegisops.execution.dto;

public record ExecutionVerificationCreateCommand(
    String id,
    String tenantId,
    String executionId,
    String stepId,
    String verificationType,
    String targetType,
    String targetId,
    String status,
    String summary,
    String detailsJson,
    String createdBy) {}

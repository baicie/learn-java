package io.aegisops.execution.dto;

public record ExecutionAuditEventCreateCommand(
    String id,
    String tenantId,
    String executionId,
    String stepId,
    String eventType,
    String actor,
    String summary,
    String payloadJson) {}

package io.aegisops.execution.dto;

public record PlanStepForExecutionRecord(
    String id,
    String planId,
    int sequenceNo,
    String name,
    String actionType,
    String targetType,
    String actionPayloadJson,
    String description,
    String expectedResult,
    String rollbackHint,
    boolean requiresApproval,
    String status) {}

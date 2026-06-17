package io.aegisops.runbook.dto;

import java.time.OffsetDateTime;

public record AutomationPlanStepResponse(
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
    String status,
    OffsetDateTime createdAt) {}

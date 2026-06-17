package io.aegisops.runbook.dto;

import java.util.Map;

public record CreateRunbookStepRequest(
    Integer sequenceNo,
    String name,
    String actionType,
    String targetType,
    String commandTemplate,
    String description,
    String expectedResult,
    String rollbackHint,
    Boolean requiresApproval,
    Integer timeoutSeconds,
    Map<String, Object> metadata) {}

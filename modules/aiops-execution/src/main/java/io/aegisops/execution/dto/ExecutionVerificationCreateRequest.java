package io.aegisops.execution.dto;

import java.util.Map;

public record ExecutionVerificationCreateRequest(
    String stepId,
    String verificationType,
    String targetType,
    String targetId,
    String status,
    String summary,
    Map<String, Object> details,
    String createdBy) {}

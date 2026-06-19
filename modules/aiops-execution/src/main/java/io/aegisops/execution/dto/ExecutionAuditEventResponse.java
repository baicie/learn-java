package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionAuditEventResponse(
    String id,
    String executionId,
    String stepId,
    String eventType,
    String actor,
    String summary,
    String payloadJson,
    OffsetDateTime createdAt) {}

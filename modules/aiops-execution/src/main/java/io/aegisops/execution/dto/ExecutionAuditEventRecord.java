package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record ExecutionAuditEventRecord(
    String id,
    String tenantId,
    String executionId,
    String stepId,
    String eventType,
    String actor,
    String summary,
    String payloadJson,
    OffsetDateTime createdAt) {}

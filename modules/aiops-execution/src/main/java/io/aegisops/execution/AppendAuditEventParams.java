package io.aegisops.execution;

import java.util.Map;

/** DTO for appending audit events. */
public record AppendAuditEventParams(
    String tenantId,
    String executionId,
    String stepId,
    String eventType,
    String actor,
    String summary,
    Map<String, ?> payload) {}

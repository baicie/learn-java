package io.aegisops.ai.client.dto;

import java.time.OffsetDateTime;

public record AgentRunRecord(
    String id,
    String diagnosisId,
    String incidentId,
    String traceId,
    String contractVersion,
    String generationMode,
    String provider,
    String model,
    String status,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    long durationMs,
    String fallbackReason,
    String safetyJson,
    String evalJson,
    OffsetDateTime createdAt) {}

package io.aegisops.ai.client.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AgentRunDetailResponse(
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
    List<AgentRunStepRecord> steps,
    List<AgentEvalResultRecord> evalResults,
    OffsetDateTime createdAt) {}

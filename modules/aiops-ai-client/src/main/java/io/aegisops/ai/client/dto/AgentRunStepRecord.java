package io.aegisops.ai.client.dto;

import java.time.OffsetDateTime;

public record AgentRunStepRecord(
    String id,
    int sequenceNo,
    String stepName,
    String stepType,
    String status,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    long durationMs,
    String inputSummary,
    String outputSummary,
    String errorMessage,
    String metadataJson) {}

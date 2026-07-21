package io.aegisops.workrecord.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AiGeneration(
    String id,
    String tenantId,
    String generationType,
    String resourceType,
    String resourceId,
    LocalDate periodStart,
    LocalDate periodEnd,
    String status,
    String promptVersion,
    String inputHash,
    String inputJson,
    String outputMarkdown,
    String provider,
    String model,
    String providerRunId,
    String providerWorkflowId,
    String providerWorkflowVersion,
    Long providerDurationMs,
    Long providerTotalTokens,
    String warningsJson,
    String fallbackReason,
    String requestedBy,
    String reviewedBy,
    OffsetDateTime reviewedAt,
    OffsetDateTime createdAt,
    OffsetDateTime finishedAt) {}

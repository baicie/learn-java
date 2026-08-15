package io.aegisops.workrecord.api;

import io.aegisops.workrecord.domain.model.AiGeneration;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AiGenerationResponse(
    String id,
    String generationType,
    String resourceType,
    String resourceId,
    LocalDate periodStart,
    LocalDate periodEnd,
    String status,
    String promptVersion,
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
    OffsetDateTime finishedAt) {

  public static AiGenerationResponse from(AiGeneration generation) {
    return new AiGenerationResponse(
        generation.id(),
        generation.generationType(),
        generation.resourceType(),
        generation.resourceId(),
        generation.periodStart(),
        generation.periodEnd(),
        generation.status(),
        generation.promptVersion(),
        generation.outputMarkdown(),
        generation.provider(),
        generation.model(),
        generation.providerRunId(),
        generation.providerWorkflowId(),
        generation.providerWorkflowVersion(),
        generation.providerDurationMs(),
        generation.providerTotalTokens(),
        generation.warningsJson(),
        generation.fallbackReason(),
        generation.requestedBy(),
        generation.reviewedBy(),
        generation.reviewedAt(),
        generation.createdAt(),
        generation.finishedAt());
  }
}

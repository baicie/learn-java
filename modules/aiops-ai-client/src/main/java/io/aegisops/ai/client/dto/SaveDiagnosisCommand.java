package io.aegisops.ai.client.dto;

/**
 * Command object for AiRepository#saveDiagnosis. Bundles the row fields so the repository signature
 * stays within the project-wide checkstyle ParameterNumber limit (5).
 */
public record SaveDiagnosisCommand(
    String id,
    String tenantId,
    String incidentId,
    AgentDiagnosisResponse response,
    String requestJson,
    String rawJson,
    String nextStepsJson,
    String runbookSuggestionsJson,
    String risksJson) {}

package io.aegisops.ai.client.dto;

import java.time.OffsetDateTime;

public record AiDiagnosisRecord(
    String id,
    String tenantId,
    String incidentId,
    String status,
    String provider,
    String model,
    String agentName,
    String summary,
    String rootCause,
    String impact,
    String nextStepsJson,
    String runbookSuggestionsJson,
    String risksJson,
    String responseRawJson,
    OffsetDateTime createdAt) {}

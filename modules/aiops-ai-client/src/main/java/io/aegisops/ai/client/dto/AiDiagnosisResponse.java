package io.aegisops.ai.client.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AiDiagnosisResponse(
    String id,
    String incidentId,
    String status,
    String provider,
    String model,
    String agentName,
    String summary,
    String rootCause,
    String impact,
    List<String> nextSteps,
    List<String> runbookSuggestions,
    List<String> risks,
    OffsetDateTime createdAt) {}

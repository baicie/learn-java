package io.aegisops.ai.client.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record AgentDiagnosisResponse(
    String contractVersion,
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
    List<String> matchedRules,
    List<String> evidenceRefs,
    List<Map<String, Object>> timeline,
    Map<String, Object> raw,
    OffsetDateTime createdAt) {}

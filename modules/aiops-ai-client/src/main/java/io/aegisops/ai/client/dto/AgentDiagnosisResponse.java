package io.aegisops.ai.client.dto;

import java.util.List;
import java.util.Map;

public record AgentDiagnosisResponse(
        String contractVersion,
        String provider,
        String model,
        String agentName,
        String summary,
        String rootCause,
        String impact,
        List<String> nextSteps,
        List<String> runbookSuggestions,
        List<String> risks,
        Map<String, Object> raw) {}

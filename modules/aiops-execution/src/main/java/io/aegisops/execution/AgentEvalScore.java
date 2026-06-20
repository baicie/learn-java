package io.aegisops.execution;

import java.util.List;

public record AgentEvalScore(
    double score,
    double rootCauseScore,
    double keywordScore,
    double actionScore,
    double safetyScore,
    boolean passed,
    List<String> matchedKeywords,
    List<String> missingKeywords,
    List<String> forbiddenHits) {}

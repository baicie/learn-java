package io.aegisops.execution.dto;

import java.util.List;

public record AgentMemorySearchResponse(
    String query, int topK, List<AgentMemorySearchResult> results) {}

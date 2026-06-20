package io.aegisops.execution.dto;

import java.util.List;

public record AgentSearchCasesRequest(
    String tenantId, String query, List<String> tags, Integer topK) {}

package io.aegisops.execution.dto;

import java.util.List;

public record KnowledgeBaseSearchResponse(
    String query, int topK, List<KnowledgeBaseSearchResult> results) {}

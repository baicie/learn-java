package io.aegisops.execution.dto;

import java.util.List;

public record KnowledgeBaseSearchRequest(
    String query, List<String> sourceTypes, List<String> tags, Integer topK, String createdBy) {}

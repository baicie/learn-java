package io.aegisops.execution.dto;

import java.util.List;

public record AgentMemorySearchRequest(
    String tenantId,
    String query,
    String scopeType,
    String scopeId,
    List<String> memoryTypes,
    List<String> tags,
    Integer topK,
    String createdBy) {}

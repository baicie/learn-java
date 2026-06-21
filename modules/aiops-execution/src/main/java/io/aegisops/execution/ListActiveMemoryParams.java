package io.aegisops.execution;

import java.util.List;

/** DTO for listing active memory candidates. */
public record ListActiveMemoryParams(
    String tenantId,
    String scopeType,
    String scopeId,
    List<String> memoryTypes,
    List<String> tags,
    int limit) {}

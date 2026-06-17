package io.aegisops.runbook.dto;

import java.time.OffsetDateTime;

public record RunbookRecord(
    String id,
    String tenantId,
    String name,
    String description,
    String category,
    String riskLevel,
    boolean enabled,
    String matchersJson,
    String variablesJson,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

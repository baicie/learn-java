package io.aegisops.runbook.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record RunbookResponse(
    String id,
    String tenantId,
    String name,
    String description,
    String category,
    String riskLevel,
    boolean enabled,
    String matchersJson,
    String variablesJson,
    List<RunbookStepTemplateRecord> steps,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

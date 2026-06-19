package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record RollbackDecisionResponse(
    String id, String reviewer, String decision, String comment, OffsetDateTime createdAt) {}

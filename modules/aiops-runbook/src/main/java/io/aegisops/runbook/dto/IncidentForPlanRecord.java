package io.aegisops.runbook.dto;

import java.time.OffsetDateTime;

public record IncidentForPlanRecord(
    String id,
    String tenantId,
    String title,
    String summary,
    String severity,
    String status,
    String primaryAssetId,
    String suspectedRootCause,
    OffsetDateTime lastSeenAt,
    OffsetDateTime updatedAt,
    OffsetDateTime createdAt) {}

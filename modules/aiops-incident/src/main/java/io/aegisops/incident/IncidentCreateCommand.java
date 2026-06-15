package io.aegisops.incident;

import java.time.OffsetDateTime;

public record IncidentCreateCommand(
    String id,
    String tenantId,
    String title,
    String summary,
    String severity,
    String source,
    String primaryAssetId,
    String aggregationKey,
    int alertCount,
    OffsetDateTime startedAt,
    OffsetDateTime detectedAt,
    OffsetDateTime lastSeenAt) {}

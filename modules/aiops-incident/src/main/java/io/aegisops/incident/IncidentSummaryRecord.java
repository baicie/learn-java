package io.aegisops.incident;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record IncidentSummaryRecord(
    String id,
    String tenantId,
    String title,
    String summary,
    String severity,
    String status,
    String source,
    String primaryAssetId,
    String aggregationKey,
    int alertCount,
    BigDecimal impactScore,
    OffsetDateTime startedAt,
    OffsetDateTime detectedAt,
    OffsetDateTime lastSeenAt,
    OffsetDateTime resolvedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

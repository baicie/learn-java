package io.aegisops.rca;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RcaIncidentRecord(
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
        String suspectedRootCause,
        BigDecimal confidence,
        OffsetDateTime startedAt,
        OffsetDateTime detectedAt,
        OffsetDateTime lastSeenAt,
        OffsetDateTime resolvedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}

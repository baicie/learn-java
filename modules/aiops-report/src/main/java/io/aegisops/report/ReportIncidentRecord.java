package io.aegisops.report;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ReportIncidentRecord(
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
    String suspectedRootCause,
    BigDecimal rcaConfidence,
    OffsetDateTime startedAt,
    OffsetDateTime detectedAt,
    OffsetDateTime lastSeenAt,
    OffsetDateTime resolvedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

package io.aegisops.ai.client.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AiIncidentRecord(
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
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

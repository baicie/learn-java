package io.aegisops.evidence.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SourceEvidenceItem(
    String type,
    String sourceId,
    String assetId,
    String serviceName,
    String traceId,
    String name,
    BigDecimal value,
    OffsetDateTime occurredAt) {}

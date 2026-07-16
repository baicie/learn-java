package io.aegisops.asset.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AssetRelationResponse(
    String id,
    String fromAssetId,
    String toAssetId,
    String relationType,
    BigDecimal confidence,
    String source,
    OffsetDateTime createdAt) {}

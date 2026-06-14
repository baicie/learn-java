package io.aegisops.rca;

import java.math.BigDecimal;

public record RcaAssetRelationRecord(
        String id,
        String fromAssetId,
        String toAssetId,
        String relationType,
        BigDecimal confidence,
        String source
) {}

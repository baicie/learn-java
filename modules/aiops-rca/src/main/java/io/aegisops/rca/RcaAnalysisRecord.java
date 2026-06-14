package io.aegisops.rca;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RcaAnalysisRecord(
        String id,
        String tenantId,
        String incidentId,
        String status,
        String suspectedRootCause,
        BigDecimal confidence,
        String summary,
        String evidenceJson,
        String modelVersion,
        OffsetDateTime createdAt
) {}

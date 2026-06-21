package io.aegisops.rca;

import java.math.BigDecimal;

/** DTO for saving RCA analysis record. */
public record SaveAnalysisParams(
    String id,
    String tenantId,
    String incidentId,
    String suspectedRootCause,
    BigDecimal confidence,
    String summary,
    String evidenceJson,
    String modelVersion) {}

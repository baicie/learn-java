package io.aegisops.rca;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record RcaAnalysisResponse(
    String id,
    String incidentId,
    String status,
    String suspectedRootCause,
    BigDecimal confidence,
    String summary,
    List<RcaEvidence> evidence,
    List<String> matchedRules,
    List<String> evidenceRefs,
    String modelVersion,
    OffsetDateTime createdAt) {}

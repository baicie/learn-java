package io.aegisops.rca;

import java.math.BigDecimal;
import java.util.List;

public record RcaAnalysisResult(
    String suspectedRootCause,
    BigDecimal confidence,
    String summary,
    List<RcaEvidence> evidence,
    List<String> matchedRules,
    List<String> evidenceRefs) {}

package io.aegisops.rca;

import java.math.BigDecimal;
import java.util.Map;

public record RcaEvidence(
        String ruleId,
        String title,
        String description,
        BigDecimal score,
        BigDecimal confidence,
        Map<String, Object> attributes
) {}

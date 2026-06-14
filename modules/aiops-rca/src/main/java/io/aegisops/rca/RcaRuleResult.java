package io.aegisops.rca;

import java.math.BigDecimal;
import java.util.List;

public record RcaRuleResult(
        String ruleId,
        String suspectedRootCause,
        BigDecimal score,
        BigDecimal confidence,
        List<RcaEvidence> evidence
) {
    public static RcaRuleResult none(String ruleId) {
        return new RcaRuleResult(ruleId, "", BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    public boolean matched() {
        return score.compareTo(BigDecimal.ZERO) > 0 && !evidence.isEmpty();
    }
}

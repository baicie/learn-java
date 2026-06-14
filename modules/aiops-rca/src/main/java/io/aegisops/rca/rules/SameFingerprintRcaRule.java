package io.aegisops.rca.rules;

import io.aegisops.rca.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SameFingerprintRcaRule implements RcaRule {
    @Override
    public String id() {
        return "R3_SAME_FINGERPRINT";
    }

    @Override
    public RcaRuleResult evaluate(RcaAnalysisContext context) {
        Map<String, List<RcaAlertRecord>> groups = context.alerts().stream()
                .filter(alert -> alert.fingerprint() != null && !alert.fingerprint().isBlank())
                .collect(Collectors.groupingBy(RcaAlertRecord::fingerprint));

        var top = groups.entrySet().stream()
                .max(Comparator.comparingInt(entry -> entry.getValue().size()))
                .orElse(null);

        if (top == null || top.getValue().size() < 2) {
            return RcaRuleResult.none(id());
        }

        int count = top.getValue().size();
        BigDecimal score = count >= 5 ? new BigDecimal("0.88") : new BigDecimal("0.70");
        BigDecimal confidence = count >= 5 ? new BigDecimal("0.76") : new BigDecimal("0.62");

        return new RcaRuleResult(
                id(),
                "Repeated alerts share the same fingerprint, suggesting the same trigger or failure mode",
                score,
                confidence,
                List.of(new RcaEvidence(
                        id(),
                        "Same fingerprint repeated",
                        count + " alerts share fingerprint " + top.getKey(),
                        score,
                        confidence,
                        Map.of(
                                "fingerprint", top.getKey(),
                                "alertCount", count
                        )
                ))
        );
    }
}

package io.aegisops.rca.rules;

import io.aegisops.rca.RcaAnalysisContext;
import io.aegisops.rca.RcaEvidence;
import io.aegisops.rca.RcaRule;
import io.aegisops.rca.RcaRuleResult;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TimelineBurstRcaRule implements RcaRule {
  @Override
  public String id() {
    return "R6_TIMELINE_BURST";
  }

  @Override
  public RcaRuleResult evaluate(RcaAnalysisContext context) {
    List<OffsetDateTime> times =
        context.alerts().stream()
            .map(alert -> alert.startsAt())
            .filter(item -> item != null)
            .sorted(Comparator.naturalOrder())
            .toList();

    if (times.size() < 2) {
      return RcaRuleResult.none(id());
    }

    OffsetDateTime first = times.get(0);
    OffsetDateTime last = times.get(times.size() - 1);
    long minutes = Math.max(0, Duration.between(first, last).toMinutes());

    if (minutes > 10) {
      return RcaRuleResult.none(id());
    }

    BigDecimal score = times.size() >= 5 ? new BigDecimal("0.80") : new BigDecimal("0.60");
    BigDecimal confidence = times.size() >= 5 ? new BigDecimal("0.68") : new BigDecimal("0.52");

    return new RcaRuleResult(
        id(),
        "Alerts occurred in a short burst, suggesting a sudden change or transient infrastructure failure",
        score,
        confidence,
        List.of(
            new RcaEvidence(
                id(),
                "Timeline burst detected",
                times.size() + " alerts occurred within " + minutes + " minute(s).",
                score,
                confidence,
                Map.of(
                    "alertCount", times.size(),
                    "durationMinutes", minutes,
                    "firstAt", first.toString(),
                    "lastAt", last.toString()))));
  }
}

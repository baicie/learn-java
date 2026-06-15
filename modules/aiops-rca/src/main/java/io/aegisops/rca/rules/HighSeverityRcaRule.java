package io.aegisops.rca.rules;

import io.aegisops.rca.*;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class HighSeverityRcaRule implements RcaRule {
  @Override
  public String id() {
    return "R1_HIGH_SEVERITY";
  }

  @Override
  public RcaRuleResult evaluate(RcaAnalysisContext context) {
    var topAlert =
        context.alerts().stream()
            .max(Comparator.comparingInt(alert -> weight(alert.severity())))
            .orElse(null);

    if (topAlert == null || weight(topAlert.severity()) < 40) {
      return RcaRuleResult.none(id());
    }

    BigDecimal score =
        "disaster".equalsIgnoreCase(topAlert.severity())
            ? new BigDecimal("0.90")
            : new BigDecimal("0.75");

    return new RcaRuleResult(
        id(),
        "High severity alert indicates the primary fault domain: " + topAlert.title(),
        score,
        new BigDecimal("0.72"),
        List.of(
            new RcaEvidence(
                id(),
                "High severity alert detected",
                "The incident contains a " + topAlert.severity() + " alert: " + topAlert.title(),
                score,
                new BigDecimal("0.72"),
                Map.of(
                    "alertId", topAlert.id(),
                    "severity", topAlert.severity(),
                    "title", topAlert.title(),
                    "assetId", topAlert.assetId() == null ? "" : topAlert.assetId()))));
  }

  private int weight(String severity) {
    if (severity == null) {
      return 10;
    }

    return switch (severity.toLowerCase()) {
      case "disaster" -> 50;
      case "critical" -> 40;
      case "warning" -> 30;
      case "low" -> 20;
      default -> 10;
    };
  }
}

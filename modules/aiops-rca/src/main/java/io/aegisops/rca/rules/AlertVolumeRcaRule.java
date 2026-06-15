package io.aegisops.rca.rules;

import io.aegisops.rca.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AlertVolumeRcaRule implements RcaRule {
  @Override
  public String id() {
    return "R2_ALERT_VOLUME";
  }

  @Override
  public RcaRuleResult evaluate(RcaAnalysisContext context) {
    int count = context.alerts().size();

    if (count < 3) {
      return RcaRuleResult.none(id());
    }

    BigDecimal score = count >= 10 ? new BigDecimal("0.85") : new BigDecimal("0.62");
    BigDecimal confidence = count >= 10 ? new BigDecimal("0.70") : new BigDecimal("0.55");

    return new RcaRuleResult(
        id(),
        "Multiple alerts were triggered together, suggesting an alert storm or shared dependency failure",
        score,
        confidence,
        List.of(
            new RcaEvidence(
                id(),
                "Alert volume is abnormal",
                "The incident contains " + count + " linked alerts.",
                score,
                confidence,
                Map.of("alertCount", count))));
  }
}

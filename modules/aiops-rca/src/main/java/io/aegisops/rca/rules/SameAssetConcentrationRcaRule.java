package io.aegisops.rca.rules;

import io.aegisops.rca.*;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SameAssetConcentrationRcaRule implements RcaRule {
  @Override
  public String id() {
    return "R4_SAME_ASSET_CONCENTRATION";
  }

  @Override
  public RcaRuleResult evaluate(RcaAnalysisContext context) {
    Map<String, List<RcaAlertRecord>> groups =
        context.alerts().stream()
            .filter(alert -> alert.assetId() != null && !alert.assetId().isBlank())
            .collect(Collectors.groupingBy(RcaAlertRecord::assetId));

    var top =
        groups.entrySet().stream()
            .max(Comparator.comparingInt(entry -> entry.getValue().size()))
            .orElse(null);

    if (top == null || top.getValue().size() < 2) {
      return RcaRuleResult.none(id());
    }

    int alertCount = context.alerts().size();
    int assetAlertCount = top.getValue().size();
    double ratio = alertCount == 0 ? 0 : (double) assetAlertCount / alertCount;

    if (ratio < 0.5) {
      return RcaRuleResult.none(id());
    }

    BigDecimal score = ratio >= 0.8 ? new BigDecimal("0.82") : new BigDecimal("0.65");
    BigDecimal confidence = ratio >= 0.8 ? new BigDecimal("0.70") : new BigDecimal("0.58");

    return new RcaRuleResult(
        id(),
        "Alerts are concentrated on one asset, suggesting a local asset fault",
        score,
        confidence,
        List.of(
            new RcaEvidence(
                id(),
                "Alerts concentrated on same asset",
                assetAlertCount
                    + " of "
                    + alertCount
                    + " alerts are linked to asset "
                    + top.getKey(),
                score,
                confidence,
                Map.of(
                    "assetId", top.getKey(),
                    "assetAlertCount", assetAlertCount,
                    "totalAlertCount", alertCount,
                    "ratio", ratio))));
  }
}

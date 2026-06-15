package io.aegisops.rca.rules;

import io.aegisops.rca.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DependencyRelationRcaRule implements RcaRule {
  private static final Set<String> STRONG_RELATIONS =
      Set.of("depends_on", "calls", "connects_to", "uses");

  @Override
  public String id() {
    return "R5_DEPENDENCY_RELATION";
  }

  @Override
  public RcaRuleResult evaluate(RcaAnalysisContext context) {
    var relation =
        context.assetRelations().stream()
            .filter(item -> item.relationType() != null)
            .filter(item -> STRONG_RELATIONS.contains(item.relationType()))
            .findFirst()
            .orElse(null);

    if (relation == null) {
      return RcaRuleResult.none(id());
    }

    BigDecimal relationConfidence =
        relation.confidence() == null ? new BigDecimal("0.50") : relation.confidence();

    BigDecimal score =
        new BigDecimal("0.68").multiply(relationConfidence).min(new BigDecimal("0.90"));
    BigDecimal confidence =
        new BigDecimal("0.60").multiply(relationConfidence).min(new BigDecimal("0.85"));

    return new RcaRuleResult(
        id(),
        "Related assets have dependency relationships, suggesting upstream/downstream propagation",
        score,
        confidence,
        List.of(
            new RcaEvidence(
                id(),
                "Dependency relation found",
                "Asset relation "
                    + relation.relationType()
                    + " exists between "
                    + relation.fromAssetId()
                    + " and "
                    + relation.toAssetId(),
                score,
                confidence,
                Map.of(
                    "relationId", relation.id(),
                    "fromAssetId", relation.fromAssetId(),
                    "toAssetId", relation.toAssetId(),
                    "relationType", relation.relationType(),
                    "relationConfidence", relationConfidence))));
  }
}

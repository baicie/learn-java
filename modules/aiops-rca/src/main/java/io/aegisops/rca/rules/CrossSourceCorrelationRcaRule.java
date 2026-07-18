package io.aegisops.rca.rules;

import io.aegisops.rca.CrossSourceCandidate;
import io.aegisops.rca.CrossSourceRcaScorer;
import io.aegisops.rca.RcaAnalysisContext;
import io.aegisops.rca.RcaEvidence;
import io.aegisops.rca.RcaRule;
import io.aegisops.rca.RcaRuleResult;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CrossSourceCorrelationRcaRule implements RcaRule {
  private final CrossSourceRcaScorer scorer;

  public CrossSourceCorrelationRcaRule(CrossSourceRcaScorer scorer) {
    this.scorer = scorer;
  }

  @Override
  public String id() {
    return "R9_CROSS_SOURCE_CORRELATION";
  }

  @Override
  public RcaRuleResult evaluate(RcaAnalysisContext context) {
    var index = context.evidenceIndex();
    String assetId = context.incident().primaryAssetId();
    List<String> refs =
        index.all().stream()
            .map(item -> item.evidenceKey())
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .toList();
    if (assetId == null || refs.isEmpty()) return RcaRuleResult.none(id());
    Map<String, BigDecimal> signals = new LinkedHashMap<>();
    signals.put(
        "entity",
        index.hasAny("asset", "zabbix", "trace", "rum") ? BigDecimal.ONE : new BigDecimal("0.4"));
    signals.put(
        "time", context.alerts().size() > 1 ? new BigDecimal("0.8") : new BigDecimal("0.5"));
    signals.put("topology", context.assetRelations().isEmpty() ? BigDecimal.ZERO : BigDecimal.ONE);
    signals.put("trigger", context.alerts().isEmpty() ? BigDecimal.ZERO : BigDecimal.ONE);
    signals.put("change", index.has("change") ? BigDecimal.ONE : BigDecimal.ZERO);
    signals.put("history", index.hasAny("history", "incident") ? BigDecimal.ONE : BigDecimal.ZERO);
    var score =
        scorer
            .topThree(
                List.of(
                    new CrossSourceCandidate(assetId, signals, refs, "确定性跨源实体、时间、拓扑、告警、变更与历史关联")))
            .getFirst();
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("assetId", assetId);
    attributes.put("subScores", score.signals());
    attributes.put("evidenceRefs", score.evidenceRefs());
    attributes.put("explanation", score.explanation());
    RcaEvidence evidence =
        new RcaEvidence(
            id(), "跨源证据关联", score.explanation(), score.score(), score.score(), attributes);
    return new RcaRuleResult(
        id(), "跨源证据共同指向资源 " + assetId, score.score(), score.score(), List.of(evidence));
  }
}

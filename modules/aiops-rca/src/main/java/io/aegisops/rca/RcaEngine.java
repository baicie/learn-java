package io.aegisops.rca;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RcaEngine {
  private static final BigDecimal MAX_CONFIDENCE = new BigDecimal("0.95");

  private final List<RcaRule> rules;

  public RcaEngine(List<RcaRule> rules) {
    this.rules = rules.stream().sorted(Comparator.comparing(RcaRule::id)).toList();
  }

  public RcaAnalysisResult analyze(RcaAnalysisContext context) {
    List<RcaRuleResult> matched =
        rules.stream()
            .map(rule -> rule.evaluate(context))
            .filter(result -> result.matched())
            .sorted(
                Comparator.comparing(RcaRuleResult::score)
                    .reversed()
                    .thenComparing(result -> result.ruleId()))
            .toList();

    if (matched.isEmpty()) {
      return new RcaAnalysisResult(
          "No strong root-cause signal found",
          new BigDecimal("0.10"),
          "No RCA rule produced enough evidence. Keep collecting metrics, logs, changes, and topology data.",
          List.of(),
          List.of(),
          List.of());
    }

    RcaRuleResult top = matched.get(0);
    List<RcaEvidence> evidence =
        matched.stream().flatMap(result -> result.evidence().stream()).toList();

    List<String> matchedRules = matched.stream().map(result -> result.ruleId()).distinct().toList();

    List<String> evidenceRefs =
        evidence.stream().flatMap(item -> extractEvidenceRefs(item).stream()).distinct().toList();

    BigDecimal confidence = aggregateConfidence(top, matchedRules, evidenceRefs);

    String summary =
        "RCA matched "
            + matched.size()
            + " rule(s), collected "
            + evidence.size()
            + " evidence item(s). Top rule: "
            + top.ruleId()
            + ".";

    return new RcaAnalysisResult(
        top.suspectedRootCause(), confidence, summary, evidence, matchedRules, evidenceRefs);
  }

  private static BigDecimal aggregateConfidence(
      RcaRuleResult top, List<String> matchedRules, List<String> evidenceRefs) {
    BigDecimal base = safeConfidence(top.confidence());

    BigDecimal ruleBoost =
        BigDecimal.valueOf(Math.max(0, matchedRules.size() - 1))
            .multiply(new BigDecimal("0.03"))
            .min(new BigDecimal("0.06"));

    BigDecimal evidenceBoost =
        BigDecimal.valueOf(Math.max(0, evidenceRefs.size() - 1))
            .multiply(new BigDecimal("0.01"))
            .min(new BigDecimal("0.03"));

    return base.add(ruleBoost)
        .add(evidenceBoost)
        .min(MAX_CONFIDENCE)
        .setScale(4, RoundingMode.HALF_UP);
  }

  private static BigDecimal safeConfidence(BigDecimal value) {
    if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
      return BigDecimal.ZERO;
    }
    if (value.compareTo(BigDecimal.ONE) > 0) {
      return BigDecimal.ONE;
    }
    return value;
  }

  private static List<String> extractEvidenceRefs(RcaEvidence evidence) {
    if (evidence == null || evidence.attributes() == null) {
      return List.of();
    }

    Object refs = evidence.attributes().get("evidenceRefs");
    if (refs instanceof Iterable<?> iterable) {
      return toStringList(iterable);
    }

    return List.of();
  }

  private static List<String> toStringList(Iterable<?> values) {
    List<String> out = new ArrayList<>();

    for (Object value : values) {
      if (value != null && !String.valueOf(value).isBlank()) {
        out.add(String.valueOf(value).trim());
      }
    }

    return List.copyOf(out);
  }
}

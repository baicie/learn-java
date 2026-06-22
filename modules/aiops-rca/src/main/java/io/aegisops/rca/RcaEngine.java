package io.aegisops.rca;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RcaEngine {
  private final List<RcaRule> rules;

  public RcaEngine(List<RcaRule> rules) {
    this.rules = rules.stream().sorted(Comparator.comparing(RcaRule::id)).toList();
  }

  public RcaAnalysisResult analyze(RcaAnalysisContext context) {
    List<RcaRuleResult> matched =
        rules.stream()
            .map(rule -> rule.evaluate(context))
            .filter(RcaRuleResult::matched)
            .sorted(Comparator.comparing(RcaRuleResult::score).reversed())
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

    List<String> matchedRules = matched.stream().map(RcaRuleResult::ruleId).distinct().toList();

    List<String> evidenceRefs =
        evidence.stream().flatMap(item -> extractEvidenceRefs(item).stream()).distinct().toList();

    BigDecimal confidence =
        matched.stream()
            .map(result -> result.confidence().multiply(normalizeScore(result.score())))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .min(new BigDecimal("0.99"))
            .setScale(4, RoundingMode.HALF_UP);

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

  private static BigDecimal normalizeScore(BigDecimal score) {
    if (score == null || score.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }

    return score.min(new BigDecimal("1.00"));
  }
}

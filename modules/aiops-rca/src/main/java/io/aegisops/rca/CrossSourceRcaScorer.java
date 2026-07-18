package io.aegisops.rca;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CrossSourceRcaScorer {
  private static final Map<String, BigDecimal> WEIGHTS =
      Map.of(
          "entity", new BigDecimal("0.25"),
          "time", new BigDecimal("0.20"),
          "topology", new BigDecimal("0.20"),
          "trigger", new BigDecimal("0.15"),
          "change", new BigDecimal("0.15"),
          "history", new BigDecimal("0.05"));

  public List<CrossSourceScore> topThree(List<CrossSourceCandidate> candidates) {
    return candidates.stream()
        .filter(candidate -> !candidate.evidenceRefs().isEmpty())
        .map(this::score)
        .sorted(
            Comparator.comparing(CrossSourceScore::score)
                .reversed()
                .thenComparing(CrossSourceScore::assetId))
        .limit(3)
        .toList();
  }

  CrossSourceScore score(CrossSourceCandidate candidate) {
    Map<String, BigDecimal> normalized = new LinkedHashMap<>();
    BigDecimal total = BigDecimal.ZERO;
    for (var weight : WEIGHTS.entrySet()) {
      BigDecimal value = clamp(candidate.signals().get(weight.getKey()));
      normalized.put(weight.getKey(), value);
      total = total.add(value.multiply(weight.getValue()));
    }
    return new CrossSourceScore(
        candidate.assetId(),
        total.setScale(4, RoundingMode.HALF_UP),
        Map.copyOf(normalized),
        candidate.evidenceRefs(),
        candidate.explanation());
  }

  private BigDecimal clamp(BigDecimal value) {
    if (value == null || value.signum() < 0) return BigDecimal.ZERO;
    return value.min(BigDecimal.ONE);
  }
}

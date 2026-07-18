package io.aegisops.rca;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrossSourceRcaScorerTest {
  @Test
  void excludesCandidatesWithoutEvidenceAndUsesStableOrdering() {
    var scorer = new CrossSourceRcaScorer();
    var sameSignals = Map.of("entity", BigDecimal.ONE, "time", BigDecimal.ONE);
    var result =
        scorer.topThree(
            List.of(
                new CrossSourceCandidate("b", sameSignals, List.of("trace:2"), "b"),
                new CrossSourceCandidate("ignored", sameSignals, List.of(), "none"),
                new CrossSourceCandidate("a", sameSignals, List.of("change:1"), "a")));
    assertEquals(List.of("a", "b"), result.stream().map(CrossSourceScore::assetId).toList());
    assertEquals(new BigDecimal("0.4500"), result.getFirst().score());
  }
}

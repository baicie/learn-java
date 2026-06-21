package io.aegisops.rca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.rca.rules.AlertVolumeRcaRule;
import io.aegisops.rca.rules.DependencyRelationRcaRule;
import io.aegisops.rca.rules.HighSeverityRcaRule;
import io.aegisops.rca.rules.SameAssetConcentrationRcaRule;
import io.aegisops.rca.rules.SameFingerprintRcaRule;
import io.aegisops.rca.rules.TimelineBurstRcaRule;
import java.util.List;
import org.junit.jupiter.api.Test;

class RcaEngineTest {
  @Test
  void analyzeReturnsFallbackWhenNoRuleMatches() {
    RcaEngine engine =
        new RcaEngine(
            List.of(
                new HighSeverityRcaRule(),
                new AlertVolumeRcaRule(),
                new SameFingerprintRcaRule(),
                new SameAssetConcentrationRcaRule(),
                new DependencyRelationRcaRule(),
                new TimelineBurstRcaRule()));

    RcaAnalysisResult result =
        engine.analyze(
            RcaTestFixtures.contextWithAlerts(
                List.of(
                    RcaTestFixtures.alert(
                        new AlertParams("a1", "info", "Info alert", "asset_1", "fp1", 0)))));

    assertEquals("No strong root-cause signal found", result.suspectedRootCause());
    assertTrue(result.evidence().isEmpty());
  }

  @Test
  void analyzeCollectsEvidenceAndReturnsTopRootCause() {
    RcaEngine engine =
        new RcaEngine(
            List.of(
                new HighSeverityRcaRule(),
                new AlertVolumeRcaRule(),
                new SameFingerprintRcaRule(),
                new SameAssetConcentrationRcaRule(),
                new DependencyRelationRcaRule(),
                new TimelineBurstRcaRule()));

    RcaAnalysisContext context =
        new RcaAnalysisContext(
            RcaTestFixtures.incident(),
            List.of(
                RcaTestFixtures.alert(
                    new AlertParams("a1", "critical", "CPU high", "asset_1", "fp_cpu", 0)),
                RcaTestFixtures.alert(
                    new AlertParams("a2", "warning", "CPU high", "asset_1", "fp_cpu", 1)),
                RcaTestFixtures.alert(
                    new AlertParams("a3", "warning", "CPU high", "asset_1", "fp_cpu", 2))),
            List.of(RcaTestFixtures.relation()));

    RcaAnalysisResult result = engine.analyze(context);

    assertNotNull(result.suspectedRootCause());
    assertFalse(result.evidence().isEmpty());
    assertTrue(result.confidence().doubleValue() > 0);
    assertTrue(result.summary().contains("RCA matched"));
  }
}

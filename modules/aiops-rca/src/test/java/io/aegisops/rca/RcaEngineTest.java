package io.aegisops.rca;

import io.aegisops.rca.rules.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RcaEngineTest {
    @Test
    void analyzeReturnsFallbackWhenNoRuleMatches() {
        RcaEngine engine = new RcaEngine(List.of(
                new HighSeverityRcaRule(),
                new AlertVolumeRcaRule(),
                new SameFingerprintRcaRule(),
                new SameAssetConcentrationRcaRule(),
                new DependencyRelationRcaRule(),
                new TimelineBurstRcaRule()
        ));

        RcaAnalysisResult result = engine.analyze(RcaTestFixtures.contextWithAlerts(List.of(
                RcaTestFixtures.alert("a1", "info", "Info alert", "asset_1", "fp1", 0)
        )));

        assertEquals("No strong root-cause signal found", result.suspectedRootCause());
        assertTrue(result.evidence().isEmpty());
    }

    @Test
    void analyzeCollectsEvidenceAndReturnsTopRootCause() {
        RcaEngine engine = new RcaEngine(List.of(
                new HighSeverityRcaRule(),
                new AlertVolumeRcaRule(),
                new SameFingerprintRcaRule(),
                new SameAssetConcentrationRcaRule(),
                new DependencyRelationRcaRule(),
                new TimelineBurstRcaRule()
        ));

        RcaAnalysisContext context = new RcaAnalysisContext(
                RcaTestFixtures.incident(),
                List.of(
                        RcaTestFixtures.alert("a1", "critical", "CPU high", "asset_1", "fp_cpu", 0),
                        RcaTestFixtures.alert("a2", "warning", "CPU high", "asset_1", "fp_cpu", 1),
                        RcaTestFixtures.alert("a3", "warning", "CPU high", "asset_1", "fp_cpu", 2)
                ),
                List.of(RcaTestFixtures.relation())
        );

        RcaAnalysisResult result = engine.analyze(context);

        assertNotNull(result.suspectedRootCause());
        assertFalse(result.evidence().isEmpty());
        assertTrue(result.confidence().doubleValue() > 0);
        assertTrue(result.summary().contains("RCA matched"));
    }
}

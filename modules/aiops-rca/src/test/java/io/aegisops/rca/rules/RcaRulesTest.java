package io.aegisops.rca.rules;

import io.aegisops.rca.RcaAnalysisContext;
import io.aegisops.rca.RcaRule;
import io.aegisops.rca.RcaRuleResult;
import io.aegisops.rca.RcaTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RcaRulesTest {
    @Test
    void highSeverityRuleMatchesCriticalAlert() {
        RcaRule rule = new HighSeverityRcaRule();
        RcaAnalysisContext context = RcaTestFixtures.contextWithAlerts(List.of(
                RcaTestFixtures.alert("a1", "critical", "CPU high", "asset_1", "fp_cpu", 0)
        ));

        RcaRuleResult result = rule.evaluate(context);

        assertTrue(result.matched());
        assertEquals("R1_HIGH_SEVERITY", result.ruleId());
        assertEquals(1, result.evidence().size());
    }

    @Test
    void alertVolumeRuleRequiresAtLeastThreeAlerts() {
        RcaRule rule = new AlertVolumeRcaRule();

        RcaRuleResult noMatch = rule.evaluate(RcaTestFixtures.contextWithAlerts(List.of(
                RcaTestFixtures.alert("a1", "warning", "A", "asset_1", "fp1", 0),
                RcaTestFixtures.alert("a2", "warning", "B", "asset_1", "fp2", 1)
        )));

        assertFalse(noMatch.matched());

        RcaRuleResult matched = rule.evaluate(RcaTestFixtures.contextWithAlerts(List.of(
                RcaTestFixtures.alert("a1", "warning", "A", "asset_1", "fp1", 0),
                RcaTestFixtures.alert("a2", "warning", "B", "asset_1", "fp2", 1),
                RcaTestFixtures.alert("a3", "warning", "C", "asset_1", "fp3", 2)
        )));

        assertTrue(matched.matched());
    }

    @Test
    void sameFingerprintRuleMatchesRepeatedFingerprint() {
        RcaRule rule = new SameFingerprintRcaRule();

        RcaRuleResult result = rule.evaluate(RcaTestFixtures.contextWithAlerts(List.of(
                RcaTestFixtures.alert("a1", "warning", "CPU high", "asset_1", "fp_cpu", 0),
                RcaTestFixtures.alert("a2", "critical", "CPU high", "asset_1", "fp_cpu", 1)
        )));

        assertTrue(result.matched());
        assertEquals("R3_SAME_FINGERPRINT", result.ruleId());
    }

    @Test
    void sameAssetRuleMatchesConcentration() {
        RcaRule rule = new SameAssetConcentrationRcaRule();

        RcaRuleResult result = rule.evaluate(RcaTestFixtures.contextWithAlerts(List.of(
                RcaTestFixtures.alert("a1", "warning", "CPU high", "asset_1", "fp1", 0),
                RcaTestFixtures.alert("a2", "critical", "Memory high", "asset_1", "fp2", 1),
                RcaTestFixtures.alert("a3", "warning", "Disk high", "asset_2", "fp3", 2)
        )));

        assertTrue(result.matched());
        assertEquals("R4_SAME_ASSET_CONCENTRATION", result.ruleId());
    }

    @Test
    void dependencyRuleMatchesStrongRelation() {
        RcaRule rule = new DependencyRelationRcaRule();

        RcaAnalysisContext context = new RcaAnalysisContext(
                RcaTestFixtures.incident(),
                List.of(RcaTestFixtures.alert("a1", "critical", "CPU high", "asset_1", "fp1", 0)),
                List.of(RcaTestFixtures.relation())
        );

        RcaRuleResult result = rule.evaluate(context);

        assertTrue(result.matched());
        assertEquals("R5_DEPENDENCY_RELATION", result.ruleId());
    }

    @Test
    void timelineBurstRuleMatchesShortWindow() {
        RcaRule rule = new TimelineBurstRcaRule();

        RcaAnalysisContext context = RcaTestFixtures.contextWithAlerts(List.of(
                RcaTestFixtures.alert("a1", "warning", "A", "asset_1", "fp1", 0),
                RcaTestFixtures.alert("a2", "warning", "B", "asset_1", "fp2", 1),
                RcaTestFixtures.alert("a3", "warning", "C", "asset_2", "fp3", 2)
        ));

        RcaRuleResult result = rule.evaluate(context);

        assertTrue(result.matched());
        assertEquals("R6_TIMELINE_BURST", result.ruleId());
    }
}

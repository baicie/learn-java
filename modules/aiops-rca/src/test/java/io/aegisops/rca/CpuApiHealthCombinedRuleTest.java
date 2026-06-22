package io.aegisops.rca;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CpuApiHealthCombinedRuleTest {
  private final CpuApiHealthCombinedRule rule = new CpuApiHealthCombinedRule();

  @Test
  void shouldMatchCpuApiAndHealthCombined() {
    RcaRuleResult result =
        rule.evaluate(
            RcaTestFixtures.contextWithEvidence(
                "metric_cpu_high", "metric_api_slow", "metric_health_check_failed"));

    assertThat(result.matched()).isTrue();
    assertThat(result.ruleId()).isEqualTo("CPU_API_HEALTH_COMBINED");
    assertThat(result.suspectedRootCause()).contains("CPU");
    assertThat(result.suspectedRootCause()).contains("健康检查失败");
    assertThat(result.confidence()).isEqualByComparingTo("0.88");
    assertThat(result.score()).isEqualByComparingTo("0.92");
  }

  @Test
  void shouldNotMatchWhenHealthMissing() {
    RcaRuleResult result =
        rule.evaluate(RcaTestFixtures.contextWithEvidence("metric_cpu_high", "metric_api_slow"));

    assertThat(result.matched()).isFalse();
  }

  @Test
  void shouldNotMatchWhenApiSlowMissing() {
    RcaRuleResult result =
        rule.evaluate(
            RcaTestFixtures.contextWithEvidence("metric_cpu_high", "metric_health_check_failed"));

    assertThat(result.matched()).isFalse();
  }

  @Test
  void shouldNotMatchWhenCpuHighMissing() {
    RcaRuleResult result =
        rule.evaluate(
            RcaTestFixtures.contextWithEvidence("metric_api_slow", "metric_health_check_failed"));

    assertThat(result.matched()).isFalse();
  }
}

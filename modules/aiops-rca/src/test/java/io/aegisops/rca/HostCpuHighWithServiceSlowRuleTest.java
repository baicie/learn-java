package io.aegisops.rca;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HostCpuHighWithServiceSlowRuleTest {
  private final HostCpuHighWithServiceSlowRule rule = new HostCpuHighWithServiceSlowRule();

  @Test
  void shouldMatchCpuHighAndApiSlow() {
    RcaRuleResult result =
        rule.evaluate(RcaTestFixtures.contextWithEvidence("metric_cpu_high", "metric_api_slow"));

    assertThat(result.matched()).isTrue();
    assertThat(result.ruleId()).isEqualTo("HOST_CPU_HIGH_WITH_SERVICE_SLOW");
    assertThat(result.suspectedRootCause()).contains("CPU");
    assertThat(result.evidence()).hasSize(1);
  }

  @Test
  void shouldNotMatchOnlyCpuHigh() {
    RcaRuleResult result = rule.evaluate(RcaTestFixtures.contextWithEvidence("metric_cpu_high"));

    assertThat(result.matched()).isFalse();
  }

  @Test
  void shouldNotMatchOnlyApiSlow() {
    RcaRuleResult result = rule.evaluate(RcaTestFixtures.contextWithEvidence("metric_api_slow"));

    assertThat(result.matched()).isFalse();
  }
}

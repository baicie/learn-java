package io.aegisops.rca;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ServiceHealthCheckFailedRuleTest {
  private final ServiceHealthCheckFailedRule rule = new ServiceHealthCheckFailedRule();

  @Test
  void shouldMatchHealthFailedAlone() {
    RcaRuleResult result =
        rule.evaluate(RcaTestFixtures.contextWithEvidence("metric_health_check_failed"));

    assertThat(result.matched()).isTrue();
    assertThat(result.ruleId()).isEqualTo("SERVICE_HEALTH_CHECK_FAILED");
    assertThat(result.suspectedRootCause()).contains("健康检查失败");
    assertThat(result.confidence()).isEqualByComparingTo("0.70");
  }

  @Test
  void shouldMatchHealthFailedWithApiSlow() {
    RcaRuleResult result =
        rule.evaluate(
            RcaTestFixtures.contextWithEvidence("metric_health_check_failed", "metric_api_slow"));

    assertThat(result.matched()).isTrue();
    assertThat(result.ruleId()).isEqualTo("SERVICE_HEALTH_CHECK_FAILED");
    assertThat(result.suspectedRootCause()).contains("健康检查失败");
    assertThat(result.confidence()).isEqualByComparingTo("0.80");
  }

  @Test
  void shouldNotMatchWithoutHealthEvidence() {
    RcaRuleResult result = rule.evaluate(RcaTestFixtures.contextWithEvidence("metric_api_slow"));

    assertThat(result.matched()).isFalse();
  }
}

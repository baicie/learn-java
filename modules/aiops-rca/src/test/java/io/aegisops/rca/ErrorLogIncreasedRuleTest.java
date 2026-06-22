package io.aegisops.rca;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorLogIncreasedRuleTest {
  private final ErrorLogIncreasedRule rule = new ErrorLogIncreasedRule();

  @Test
  void shouldMatchErrorLogIncreasedWithTimeout() {
    RcaAnalysisContext context =
        RcaTestFixtures.contextWithEvidence(
            RcaTestFixtures.evidence(
                "evd_log",
                "metric_error_log_increased",
                "错误日志数量增加",
                "Timeout while creating order"));

    RcaRuleResult result = rule.evaluate(context);

    assertThat(result.matched()).isTrue();
    assertThat(result.ruleId()).isEqualTo("ERROR_LOG_INCREASED_WITH_TIMEOUT");
    assertThat(result.suspectedRootCause()).contains("Timeout");
    assertThat(result.confidence()).isEqualByComparingTo("0.78");
  }

  @Test
  void shouldMatchErrorLogIncreasedWithoutTimeout() {
    RcaRuleResult result =
        rule.evaluate(RcaTestFixtures.contextWithEvidence("metric_error_log_increased"));

    assertThat(result.matched()).isTrue();
    assertThat(result.ruleId()).isEqualTo("ERROR_LOG_INCREASED_WITH_TIMEOUT");
    assertThat(result.suspectedRootCause()).doesNotContain("Timeout");
    assertThat(result.confidence()).isEqualByComparingTo("0.66");
  }

  @Test
  void shouldNotMatchWithoutErrorLogEvidence() {
    RcaRuleResult result = rule.evaluate(RcaTestFixtures.contextWithEvidence("metric_api_slow"));

    assertThat(result.matched()).isFalse();
  }
}

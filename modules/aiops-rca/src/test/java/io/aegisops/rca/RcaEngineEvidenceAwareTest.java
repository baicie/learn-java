package io.aegisops.rca;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RcaEngineEvidenceAwareTest {
  @Test
  void shouldReturnCombinedRootCauseAndEvidenceRefs() {
    RcaEngine engine =
        new RcaEngine(
            List.of(
                new HostCpuHighWithServiceSlowRule(),
                new ServiceHealthCheckFailedRule(),
                new ErrorLogIncreasedRule(),
                new CpuApiHealthCombinedRule()));

    RcaAnalysisResult result =
        engine.analyze(
            RcaTestFixtures.contextWithEvidence(
                "metric_cpu_high",
                "metric_api_slow",
                "metric_health_check_failed",
                "metric_error_log_increased"));

    assertThat(result.suspectedRootCause()).contains("CPU");
    assertThat(result.confidence()).isLessThanOrEqualTo(new BigDecimal("0.95"));
    assertThat(result.matchedRules())
        .contains(
            "CPU_API_HEALTH_COMBINED",
            "HOST_CPU_HIGH_WITH_SERVICE_SLOW",
            "SERVICE_HEALTH_CHECK_FAILED");
    assertThat(result.evidenceRefs())
        .contains("evd_metric_cpu_high", "evd_metric_api_slow", "evd_metric_health_check_failed");
  }

  @Test
  void shouldReturnNoSignalWhenEvidenceMissing() {
    RcaEngine engine = new RcaEngine(List.of(new CpuApiHealthCombinedRule()));

    RcaAnalysisResult result =
        engine.analyze(
            new RcaAnalysisContext(RcaTestFixtures.incident(), List.of(), List.of(), List.of()));

    assertThat(result.suspectedRootCause()).isEqualTo("No strong root-cause signal found");
    assertThat(result.matchedRules()).isEmpty();
    assertThat(result.evidenceRefs()).isEmpty();
  }

  @Test
  void shouldPreferCpuApiHealthCombinedAsTopRule() {
    RcaEngine engine =
        new RcaEngine(
            List.of(new HostCpuHighWithServiceSlowRule(), new CpuApiHealthCombinedRule()));

    RcaAnalysisResult result =
        engine.analyze(
            RcaTestFixtures.contextWithEvidence(
                "metric_cpu_high", "metric_api_slow", "metric_health_check_failed"));

    assertThat(result.matchedRules())
        .containsExactly("CPU_API_HEALTH_COMBINED", "HOST_CPU_HIGH_WITH_SERVICE_SLOW");
    assertThat(result.suspectedRootCause()).contains("健康检查失败");
  }

  @Test
  void shouldCapConfidenceWithoutOverstatingCertainty() {
    RcaEngine engine =
        new RcaEngine(
            List.of(
                new HostCpuHighWithServiceSlowRule(),
                new ServiceHealthCheckFailedRule(),
                new ErrorLogIncreasedRule(),
                new CpuApiHealthCombinedRule()));

    RcaAnalysisResult result =
        engine.analyze(
            RcaTestFixtures.contextWithEvidence(
                "metric_cpu_high",
                "metric_api_slow",
                "metric_health_check_failed",
                "metric_error_log_increased"));

    assertThat(result.confidence()).isLessThanOrEqualTo(new BigDecimal("0.95"));
    assertThat(result.confidence()).isGreaterThanOrEqualTo(new BigDecimal("0.80"));
  }
}

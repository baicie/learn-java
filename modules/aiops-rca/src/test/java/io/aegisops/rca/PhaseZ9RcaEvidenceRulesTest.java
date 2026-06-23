package io.aegisops.rca;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhaseZ9RcaEvidenceRulesTest {

  @Test
  void shouldMatchZabbixMvpCombinedEvidenceRules() {
    RcaEngine engine =
        new RcaEngine(
            List.of(
                new HostCpuHighWithServiceSlowRule(),
                new ServiceHealthCheckFailedRule(),
                new ErrorLogIncreasedRule(),
                new CpuApiHealthCombinedRule()));

    RcaAnalysisResult result =
        engine.analyze(
            new RcaAnalysisContext(
                incident(),
                List.of(),
                List.of(),
                List.of(
                    evidence("evd_cpu", "metric_cpu_high", "CPU 最大值 96%"),
                    evidence("evd_api", "metric_api_slow", "接口响应时间最大值 2.50s"),
                    evidence("evd_health", "metric_health_check_failed", "/health 连续失败"),
                    evidence("evd_error", "metric_error_log_increased", "Timeout 日志数量增加"))));

    assertThat(result.suspectedRootCause()).contains("CPU");
    assertThat(result.matchedRules())
        .contains(
            "CPU_API_HEALTH_COMBINED",
            "HOST_CPU_HIGH_WITH_SERVICE_SLOW",
            "SERVICE_HEALTH_CHECK_FAILED",
            "ERROR_LOG_INCREASED_WITH_TIMEOUT");
    assertThat(result.evidenceRefs()).contains("evd_cpu", "evd_api", "evd_health");
    assertThat(result.confidence()).isGreaterThanOrEqualTo(new BigDecimal("0.88"));
  }

  private static RcaIncidentRecord incident() {
    return new RcaIncidentRecord(
        "inc_z9",
        "tenant_z9",
        "order-service 主机与服务异常",
        null,
        "critical",
        "open",
        "zabbix:ds_zabbix_z9:10084:order-service:demo:202606210510",
        null,
        null,
        4,
        null,
        null,
        OffsetDateTime.parse("2026-06-21T05:10:00Z"),
        OffsetDateTime.parse("2026-06-21T05:10:00Z"),
        null,
        null,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private static RcaDiagnosisEvidenceRecord evidence(String key, String type, String summary) {
    return new RcaDiagnosisEvidenceRecord(
        "id_" + key,
        "inc_z9",
        key,
        "zabbix",
        type,
        type,
        summary,
        OffsetDateTime.parse("2026-06-21T05:00:00Z"),
        OffsetDateTime.parse("2026-06-21T05:30:00Z"),
        BigDecimal.valueOf(0.86),
        """
        {
          "summary": "%s"
        }
        """
            .formatted(summary));
  }
}

package io.aegisops.incident;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncidentAggregationPolicyTest {
  private final IncidentAggregationPolicy policy = new IncidentAggregationPolicy();

  @Test
  void shouldUseAggregationKeyBeforeFingerprint() {
    AlertCandidate alert =
        candidate(
            "alert_1",
            "AegisOps Demo CPU High",
            "high",
            "zabbix:ds_1:10084:order-service:demo:202606210510",
            "zabbix:ds_1:trigger_1");

    assertThat(policy.aggregationKey(alert))
        .isEqualTo("zabbix:ds_1:10084:order-service:demo:202606210510");
  }

  @Test
  void shouldFallbackToFingerprintWhenAggregationKeyMissing() {
    AlertCandidate alert =
        candidate("alert_1", "AegisOps Demo CPU High", "high", null, "zabbix:ds_1:trigger_1");

    assertThat(policy.aggregationKey(alert)).isEqualTo("zabbix:ds_1:trigger_1");
  }

  @Test
  void shouldBuildZabbixIncidentTitle() {
    List<AlertCandidate> alerts =
        List.of(
            candidate("a1", "CPU High", "high", "key", "fp1"),
            candidate("a2", "API Slow", "medium", "key", "fp2"));

    assertThat(policy.title("key", alerts)).isEqualTo("order-service 主机与服务异常");
  }

  @Test
  void shouldPickHighestSeverity() {
    List<AlertCandidate> alerts =
        List.of(
            candidate("a1", "CPU High", "warning", "key", "fp1"),
            candidate("a2", "Health Check Failed", "critical", "key", "fp2"),
            candidate("a3", "API Slow", "medium", "key", "fp3"));

    assertThat(policy.highestSeverity(alerts)).isEqualTo("critical");
  }

  private AlertCandidate candidate(
      String id, String title, String severity, String aggregationKey, String fingerprint) {
    return new AlertCandidate(
        id,
        "tenant_1",
        "zabbix",
        "ds_1:" + id,
        severity,
        title,
        title,
        "asset_1",
        "service",
        "order-service",
        fingerprint,
        aggregationKey,
        "{}",
        OffsetDateTime.parse("2026-06-21T05:10:00Z"),
        null,
        OffsetDateTime.parse("2026-06-21T05:10:00Z"));
  }
}

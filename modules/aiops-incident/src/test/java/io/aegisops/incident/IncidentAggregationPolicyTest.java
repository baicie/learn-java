package io.aegisops.incident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncidentAggregationPolicyTest {
  private final IncidentAggregationPolicy policy = new IncidentAggregationPolicy();

  @Test
  void usesFingerprintAsAggregationKey() {
    AlertCandidate alert =
        alert("a1", "zabbix", "critical", "CPU high", "asset_1", "zabbix:ds_1:trigger_1");

    assertEquals("zabbix:zabbix:ds_1:trigger_1", policy.aggregationKey(alert));
  }

  @Test
  void fallsBackToAssetAndNormalizedTitleWhenFingerprintMissing() {
    AlertCandidate alert = alert("a1", "webhook", "warning", "Disk Usage > 90%", "asset_1", "");

    assertEquals("webhook:asset_1:disk-usage-90", policy.aggregationKey(alert));
  }

  @Test
  void selectsHighestSeverity() {
    List<AlertCandidate> alerts =
        List.of(
            alert("a1", "zabbix", "warning", "CPU high", "asset_1", "fp"),
            alert("a2", "zabbix", "critical", "CPU high", "asset_1", "fp"),
            alert("a3", "zabbix", "info", "CPU high", "asset_1", "fp"));

    assertEquals("critical", policy.highestSeverity(alerts));
  }

  @Test
  void titleUsesSameAlertTitleWhenAllSame() {
    List<AlertCandidate> alerts =
        List.of(
            alert("a1", "zabbix", "warning", "CPU high", "asset_1", "fp"),
            alert("a2", "zabbix", "critical", "CPU high", "asset_1", "fp"));

    assertEquals("CPU high", policy.title("zabbix:fp", alerts));
  }

  @Test
  void summaryContainsAlertCountAndAggregationKey() {
    List<AlertCandidate> alerts =
        List.of(
            alert("a1", "zabbix", "warning", "CPU high", "asset_1", "fp"),
            alert("a2", "zabbix", "critical", "CPU high", "asset_1", "fp"));

    String summary = policy.summary("zabbix:fp", alerts);

    assertTrue(summary.contains("2 alert"));
    assertTrue(summary.contains("zabbix:fp"));
    assertTrue(summary.contains("critical"));
  }

  private AlertCandidate alert(
      String id, String source, String severity, String title, String assetId, String fingerprint) {
    return new AlertCandidate(
        id,
        "tenant_1",
        source,
        "source_" + id,
        severity,
        title,
        "desc " + id,
        assetId,
        "host",
        "host-1",
        fingerprint,
        OffsetDateTime.parse("2026-06-14T10:00:00+09:00"),
        OffsetDateTime.parse("2026-06-14T10:00:00+09:00"));
  }
}

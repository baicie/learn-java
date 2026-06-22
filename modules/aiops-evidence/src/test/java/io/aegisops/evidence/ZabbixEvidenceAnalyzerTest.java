package io.aegisops.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.zabbix.ZabbixHistoryPoint;
import io.aegisops.zabbix.ZabbixItem;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ZabbixEvidenceAnalyzerTest {
  private final ZabbixEvidenceAnalyzer analyzer = new ZabbixEvidenceAnalyzer();

  @Test
  void shouldCreateCpuHighEvidence() {
    ZabbixItem item =
        new ZabbixItem(
            "item_cpu",
            "10084",
            "AegisOps Demo CPU Utilization",
            "demo.cpu.util",
            0,
            "%",
            "19",
            "10s",
            Map.of(),
            Map.of());

    var evidence =
        analyzer.analyze(
            new ZabbixEvidenceAnalyzer.AnalysisQuery(
                "inc_1",
                "10084",
                item,
                List.of(point("item_cpu", "20"), point("item_cpu", "95"), point("item_cpu", "96")),
                List.of(),
                OffsetDateTime.parse("2026-06-21T05:00:00Z"),
                OffsetDateTime.parse("2026-06-21T05:30:00Z")));

    assertThat(evidence).isPresent();
    assertThat(evidence.get().evidenceType()).isEqualTo("metric_cpu_high");
    assertThat(evidence.get().title()).contains("CPU");
    assertThat(evidence.get().summary()).contains("96.00");
  }

  @Test
  void shouldNotCreateCpuEvidenceWhenBelowThreshold() {
    ZabbixItem item =
        new ZabbixItem(
            "item_cpu", "10084", "CPU", "demo.cpu.util", 0, "%", "19", "10s", Map.of(), Map.of());

    var evidence =
        analyzer.analyze(
            new ZabbixEvidenceAnalyzer.AnalysisQuery(
                "inc_1",
                "10084",
                item,
                List.of(point("item_cpu", "20"), point("item_cpu", "30")),
                List.of(),
                OffsetDateTime.parse("2026-06-21T05:00:00Z"),
                OffsetDateTime.parse("2026-06-21T05:30:00Z")));

    assertThat(evidence).isEmpty();
  }

  @Test
  void shouldCreateApiSlowEvidence() {
    ZabbixItem item =
        new ZabbixItem(
            "item_api",
            "10084",
            "AegisOps Demo Order Create Time",
            "demo.order.create.time",
            0,
            "s",
            "19",
            "10s",
            Map.of(),
            Map.of());

    var evidence =
        analyzer.analyze(
            new ZabbixEvidenceAnalyzer.AnalysisQuery(
                "inc_1",
                "10084",
                item,
                List.of(point("item_api", "0.12"), point("item_api", "2.50")),
                List.of(),
                OffsetDateTime.parse("2026-06-21T05:00:00Z"),
                OffsetDateTime.parse("2026-06-21T05:30:00Z")));

    assertThat(evidence).isPresent();
    assertThat(evidence.get().evidenceType()).isEqualTo("metric_api_slow");
    assertThat(evidence.get().summary()).contains("2.50");
  }

  @Test
  void shouldCreateHealthFailedEvidence() {
    ZabbixItem item =
        new ZabbixItem(
            "item_health",
            "10084",
            "AegisOps Demo Health Status",
            "demo.health.status",
            3,
            "",
            "19",
            "10s",
            Map.of(),
            Map.of());

    var evidence =
        analyzer.analyze(
            new ZabbixEvidenceAnalyzer.AnalysisQuery(
                "inc_1",
                "10084",
                item,
                List.of(point("item_health", "1"), point("item_health", "0")),
                List.of(),
                OffsetDateTime.parse("2026-06-21T05:00:00Z"),
                OffsetDateTime.parse("2026-06-21T05:30:00Z")));

    assertThat(evidence).isPresent();
    assertThat(evidence.get().evidenceType()).isEqualTo("metric_health_check_failed");
  }

  private ZabbixHistoryPoint point(String itemId, String value) {
    return new ZabbixHistoryPoint(
        itemId, 0, Instant.parse("2026-06-21T05:10:00Z"), value, Map.of());
  }
}

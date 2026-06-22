package io.aegisops.rca;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RcaEvidenceIndexTest {
  @Test
  void shouldIndexEvidenceByType() {
    RcaEvidenceIndex index =
        RcaEvidenceIndex.from(
            List.of(
                evidence("evd_cpu", "metric_cpu_high", "CPU high"),
                evidence("evd_api", "metric_api_slow", "API slow")));

    assertThat(index.has("metric_cpu_high")).isTrue();
    assertThat(index.has("metric_api_slow")).isTrue();
    assertThat(index.has("metric_health_check_failed")).isFalse();
    assertThat(index.refs("metric_cpu_high")).containsExactly("evd_cpu");
  }

  @Test
  void shouldFindKeywordInSummaryOrPayload() {
    RcaEvidenceIndex index =
        RcaEvidenceIndex.from(
            List.of(
                evidence("evd_log", "metric_error_log_increased", "Timeout while creating order")));

    assertThat(index.containsText("metric_error_log_increased", "timeout")).isTrue();
  }

  @Test
  void shouldReturnEmptyForMissingType() {
    RcaEvidenceIndex index = RcaEvidenceIndex.from(List.of());

    assertThat(index.has("metric_cpu_high")).isFalse();
    assertThat(index.get("metric_cpu_high")).isEmpty();
  }

  @Test
  void shouldCollectRefsAcrossTypes() {
    RcaEvidenceIndex index =
        RcaEvidenceIndex.from(
            List.of(
                evidence("evd_cpu", "metric_cpu_high", "CPU"),
                evidence("evd_api", "metric_api_slow", "API")));

    assertThat(index.refs("metric_cpu_high", "metric_api_slow"))
        .containsExactly("evd_cpu", "evd_api");
  }

  @Test
  void shouldHandleNullEvidenceList() {
    RcaEvidenceIndex index = RcaEvidenceIndex.from(null);

    assertThat(index.has("metric_cpu_high")).isFalse();
    assertThat(index.all()).isEmpty();
  }

  private RcaDiagnosisEvidenceRecord evidence(String key, String type, String summary) {
    return new RcaDiagnosisEvidenceRecord(
        "id_" + key,
        "inc_1",
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
          "message": "%s"
        }
        """
            .formatted(summary));
  }
}

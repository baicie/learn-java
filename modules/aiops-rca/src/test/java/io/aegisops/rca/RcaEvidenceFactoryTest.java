package io.aegisops.rca;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RcaEvidenceFactoryTest {
  @Test
  void shouldAttachDescriptionFromDiagnosisEvidenceSummary() {
    RcaEvidence evidence =
        RcaEvidenceFactory.fromDiagnosisEvidence(
            "RULE_1",
            "CPU high",
            new BigDecimal("0.80"),
            new BigDecimal("0.80"),
            List.of(
                new RcaDiagnosisEvidenceRecord(
                    "evd_cpu",
                    "inc_1",
                    "evd_cpu",
                    "zabbix",
                    "metric_cpu_high",
                    "CPU high",
                    "CPU 最大值 96%",
                    OffsetDateTime.parse("2026-06-21T05:00:00Z"),
                    OffsetDateTime.parse("2026-06-21T05:30:00Z"),
                    BigDecimal.valueOf(0.86),
                    "{\"message\":\"CPU 96%\"}")));

    assertThat(evidence.description()).contains("CPU 最大值 96%");
    assertThat(evidence.attributes()).containsKey("evidenceRefs");
  }

  @Test
  void shouldCombineMultipleSummariesWithSemicolon() {
    RcaEvidence evidence =
        RcaEvidenceFactory.fromDiagnosisEvidence(
            "RULE_2",
            "Service slow",
            new BigDecimal("0.78"),
            new BigDecimal("0.80"),
            List.of(
                new RcaDiagnosisEvidenceRecord(
                    "evd_1",
                    "inc_1",
                    "evd_1",
                    "zabbix",
                    "metric_api_slow",
                    "API slow",
                    "响应 2.1s",
                    OffsetDateTime.now(),
                    OffsetDateTime.now(),
                    BigDecimal.valueOf(0.8),
                    "{}"),
                new RcaDiagnosisEvidenceRecord(
                    "evd_2",
                    "inc_1",
                    "evd_2",
                    "zabbix",
                    "metric_health_check_failed",
                    "Health",
                    "Failed",
                    OffsetDateTime.now(),
                    OffsetDateTime.now(),
                    BigDecimal.valueOf(0.75),
                    "{}")));

    assertThat(evidence.description()).contains("响应 2.1s");
    assertThat(evidence.description()).contains("Failed");
  }

  @Test
  void shouldFallBackToNoEvidenceMessageWhenRefsEmpty() {
    RcaEvidence evidence =
        RcaEvidenceFactory.fromDiagnosisEvidence(
            "RULE_3", "No evidence", new BigDecimal("0.50"), new BigDecimal("0.50"), List.of());

    assertThat(evidence.description()).contains("No diagnosis evidence refs attached");
  }

  @Test
  void shouldAttachEvidenceRefsAndTypes() {
    List<RcaDiagnosisEvidenceRecord> refs =
        List.of(
            new RcaDiagnosisEvidenceRecord(
                "evd_x",
                "inc_1",
                "evd_x",
                "zabbix",
                "metric_cpu_high",
                "CPU",
                "CPU",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                BigDecimal.ONE,
                "{}"));

    RcaEvidence evidence =
        RcaEvidenceFactory.fromDiagnosisEvidence(
            "RULE_4", "CPU", new BigDecimal("0.82"), new BigDecimal("0.82"), refs);

    assertThat(evidence.attributes()).containsEntry("evidenceRefs", List.of("evd_x"));
    assertThat(evidence.attributes()).containsEntry("evidenceTypes", List.of("metric_cpu_high"));
    assertThat(evidence.attributes()).containsEntry("source", "diagnosis_evidence");
  }
}

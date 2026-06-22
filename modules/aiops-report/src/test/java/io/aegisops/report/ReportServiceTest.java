package io.aegisops.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReportServiceTest {
  @Test
  void shouldGenerateIncidentReport() {
    FakeReportRepository repository = new FakeReportRepository();
    ReportService service =
        new ReportService(
            repository, new MarkdownReportRenderer(new ObjectMapper()), new ObjectMapper());

    IncidentReportResponse response =
        service.generate(
            "tenant_1", "inc_1", new GenerateIncidentReportRequest(true, "zh-CN", "tester"));

    assertThat(response.id()).startsWith("rpt_");
    assertThat(response.versionNo()).isEqualTo(1);
    assertThat(response.markdownContent()).contains("# 故障报告：order-service 主机与服务异常");
    assertThat(response.markdownContent()).contains("CPU 使用率持续高位");
    assertThat(response.snapshotJson()).contains("evd_cpu");
    assertThat(repository.insertedReports).hasSize(1);
  }

  @Test
  void shouldReturnLatestWhenForceFalse() {
    FakeReportRepository repository = new FakeReportRepository();
    repository.latestReport =
        Optional.of(
            new IncidentReportRecord(
                "rpt_old",
                "tenant_1",
                "inc_1",
                1,
                "incident_markdown",
                "markdown",
                "old",
                "# old",
                "{}",
                "tester",
                OffsetDateTime.parse("2026-06-21T05:30:00Z"),
                OffsetDateTime.parse("2026-06-21T05:30:00Z")));

    ReportService service =
        new ReportService(
            repository, new MarkdownReportRenderer(new ObjectMapper()), new ObjectMapper());

    IncidentReportResponse response =
        service.generate(
            "tenant_1", "inc_1", new GenerateIncidentReportRequest(false, "zh-CN", "tester"));

    assertThat(response.id()).isEqualTo("rpt_old");
    assertThat(repository.insertedReports).isEmpty();
  }

  @Test
  void shouldFailWhenIncidentMissing() {
    FakeReportRepository repository = new FakeReportRepository();
    repository.incident = Optional.empty();

    ReportService service =
        new ReportService(
            repository, new MarkdownReportRenderer(new ObjectMapper()), new ObjectMapper());

    assertThatThrownBy(
            () ->
                service.generate(
                    "tenant_1",
                    "inc_missing",
                    new GenerateIncidentReportRequest(true, "zh-CN", "tester")))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("Incident not found");
  }

  private static class FakeReportRepository implements ReportRepository {
    private Optional<ReportIncidentRecord> incident = Optional.of(incident());
    private Optional<IncidentReportRecord> latestReport = Optional.empty();
    private final List<IncidentReportRecord> insertedReports = new ArrayList<>();

    @Override
    public Optional<ReportIncidentRecord> findIncident(String tenantId, String incidentId) {
      return incident;
    }

    @Override
    public List<ReportAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
      OffsetDateTime t = OffsetDateTime.parse("2026-06-21T05:10:00Z");
      return List.of(
          new ReportAlertRecord(
              "a1",
              "zabbix",
              "ds_1:20001",
              "high",
              "CPU High",
              "CPU high",
              "asset_1",
              "service",
              "order-service",
              "open",
              "fp1",
              "agg1",
              "{}",
              t,
              null,
              t));
    }

    @Override
    public List<ReportEvidenceRecord> listEvidence(String tenantId, String incidentId) {
      OffsetDateTime t = OffsetDateTime.parse("2026-06-21T05:10:00Z");
      return List.of(
          new ReportEvidenceRecord(
              "evd_1",
              "evd_cpu",
              "zabbix",
              "metric_cpu_high",
              "CPU 使用率持续高位",
              "CPU 最大值 96%",
              t.minusMinutes(10),
              t.plusMinutes(20),
              new BigDecimal("0.86"),
              "{}",
              t));
    }

    @Override
    public Optional<ReportRcaRecord> findLatestRca(String tenantId, String incidentId) {
      return Optional.of(
          new ReportRcaRecord(
              "rca_1",
              "疑似 CPU 饱和导致服务响应变慢",
              new BigDecimal("0.88"),
              "RCA matched 3 rules",
              """
              [
                {
                  "ruleId": "CPU_API_HEALTH_COMBINED"
                }
              ]
              """,
              "rules-v2-evidence",
              OffsetDateTime.parse("2026-06-21T05:20:00Z")));
    }

    @Override
    public Optional<ReportAiDiagnosisRecord> findLatestAiDiagnosis(
        String tenantId, String incidentId) {
      return Optional.of(
          new ReportAiDiagnosisRecord(
              "ai_1",
              "aiops-agent",
              "langgraph-deterministic",
              "aegisops_diagnosis_graph",
              "order-service 出现 CPU 高位。",
              "疑似 CPU 饱和。",
              "影响 order-service。",
              """
              ["查看 CPU Top 进程"]
              """,
              """
              ["主机 CPU 高位排查 Runbook"]
              """,
              "[]",
              """
              {
                "matchedRules": ["CPU_API_HEALTH_COMBINED"],
                "evidenceRefs": ["evd_cpu"]
              }
              """,
              OffsetDateTime.parse("2026-06-21T05:21:00Z")));
    }

    @Override
    public List<ReportTimelineRecord> listTimeline(String tenantId, String incidentId, int limit) {
      return List.of(
          new ReportTimelineRecord(
              "tl_1",
              OffsetDateTime.parse("2026-06-21T05:10:00Z"),
              "alert_linked",
              "CPU High",
              "CPU alert linked",
              "system",
              "{}"));
    }

    @Override
    public Optional<IncidentReportRecord> findLatestReport(String tenantId, String incidentId) {
      return latestReport;
    }

    @Override
    public int nextVersionNo(String tenantId, String incidentId) {
      return insertedReports.size() + 1;
    }

    @Override
    public IncidentReportRecord insertReport(InsertReportParams params) {
      IncidentReportRecord report =
          new IncidentReportRecord(
              params.id(),
              params.tenantId(),
              params.incidentId(),
              params.versionNo(),
              "incident_markdown",
              "markdown",
              params.title(),
              params.markdownContent(),
              params.snapshotJson(),
              params.createdBy(),
              OffsetDateTime.parse("2026-06-21T05:30:00Z"),
              OffsetDateTime.parse("2026-06-21T05:30:00Z"));

      insertedReports.add(report);
      latestReport = Optional.of(report);
      return report;
    }

    private static ReportIncidentRecord incident() {
      OffsetDateTime t = OffsetDateTime.parse("2026-06-21T05:10:00Z");

      return new ReportIncidentRecord(
          "inc_1",
          "tenant_1",
          "order-service 主机与服务异常",
          "summary",
          "critical",
          "open",
          "zabbix",
          "asset_1",
          "agg1",
          1,
          BigDecimal.ZERO,
          "疑似 CPU 饱和",
          new BigDecimal("0.88"),
          t,
          t,
          t.plusMinutes(10),
          null,
          t,
          t.plusMinutes(10));
    }
  }
}

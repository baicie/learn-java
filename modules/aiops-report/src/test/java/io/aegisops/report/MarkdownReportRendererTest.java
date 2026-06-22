package io.aegisops.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownReportRendererTest {
  private final MarkdownReportRenderer renderer = new MarkdownReportRenderer(new ObjectMapper());

  @Test
  void shouldRenderMarkdownReportWithCoreSections() {
    String markdown = renderer.render(context());

    assertThat(markdown).contains("# 故障报告：order-service 主机与服务异常");
    assertThat(markdown).contains("## 一、故障摘要");
    assertThat(markdown).contains("## 二、影响范围");
    assertThat(markdown).contains("## 三、时间线");
    assertThat(markdown).contains("## 四、关联告警");
    assertThat(markdown).contains("## 五、关键证据");
    assertThat(markdown).contains("## 六、RCA 根因判断");
    assertThat(markdown).contains("## 七、AI 诊断");
    assertThat(markdown).contains("## 八、处理建议");

    assertThat(markdown).contains("CPU 使用率持续高位");
    assertThat(markdown).contains("接口响应时间明显升高");
    assertThat(markdown).contains("CPU_API_HEALTH_COMBINED");
    assertThat(markdown).contains("查看 CPU Top 进程");
  }

  private ReportContext context() {
    OffsetDateTime t = OffsetDateTime.parse("2026-06-21T05:10:00Z");
    return new ReportContext(
        incident(t), List.of(alert(t)), evidence(t), rca(t), aiDiagnosis(t), timeline(t), "zh-CN");
  }

  private ReportIncidentRecord incident(OffsetDateTime t) {
    return new ReportIncidentRecord(
        "inc_1",
        "tenant_1",
        "order-service 主机与服务异常",
        "Zabbix 检测到 order-service 异常",
        "critical",
        "open",
        "zabbix",
        "asset_1",
        "zabbix:ds_1:10084:order-service:demo:202606210510",
        3,
        BigDecimal.ZERO,
        "疑似 CPU 饱和导致服务异常",
        new BigDecimal("0.88"),
        t,
        t,
        t.plusMinutes(10),
        null,
        t,
        t.plusMinutes(10));
  }

  private ReportAlertRecord alert(OffsetDateTime t) {
    return new ReportAlertRecord(
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
        t);
  }

  private List<ReportEvidenceRecord> evidence(OffsetDateTime t) {
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
            t),
        new ReportEvidenceRecord(
            "evd_2",
            "evd_api",
            "zabbix",
            "metric_api_slow",
            "接口响应时间明显升高",
            "接口响应时间最大值 2.50s",
            t.minusMinutes(10),
            t.plusMinutes(20),
            new BigDecimal("0.86"),
            "{}",
            t));
  }

  private ReportRcaRecord rca(OffsetDateTime t) {
    return new ReportRcaRecord(
        "rca_1",
        "疑似主机 CPU 饱和导致服务响应变慢",
        new BigDecimal("0.88"),
        "RCA matched 3 rules",
        "[{\"ruleId\": \"CPU_API_HEALTH_COMBINED\"}]",
        "rules-v2-evidence",
        t.plusMinutes(11));
  }

  private ReportAiDiagnosisRecord aiDiagnosis(OffsetDateTime t) {
    return new ReportAiDiagnosisRecord(
        "ai_1",
        "aiops-agent",
        "langgraph-deterministic",
        "aegisops_diagnosis_graph",
        "order-service 同时出现 CPU 高位、接口慢和健康检查失败。",
        "疑似 CPU 饱和导致服务响应变慢。",
        "影响 order-service 可用性。",
        "[\"查看 CPU Top 进程\", \"检查最近发布或批处理任务\"]",
        "[\"主机 CPU 高位排查 Runbook\"]",
        "[\"证据不足时需要补充日志\"]",
        "{\"matchedRules\": [\"CPU_API_HEALTH_COMBINED\"], \"evidenceRefs\": [\"evd_cpu\", \"evd_api\"]}",
        t.plusMinutes(12));
  }

  private List<ReportTimelineRecord> timeline(OffsetDateTime t) {
    return List.of(
        new ReportTimelineRecord(
            "tl_1", t, "alert_linked", "CPU High", "CPU alert linked", "system", "{}"));
  }
}

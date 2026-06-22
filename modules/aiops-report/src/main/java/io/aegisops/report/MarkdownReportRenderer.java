package io.aegisops.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MarkdownReportRenderer {
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

  private final ObjectMapper objectMapper;

  public MarkdownReportRenderer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String render(ReportContext context) {
    StringBuilder md = new StringBuilder();

    ReportIncidentRecord incident = context.incident();
    ReportAiDiagnosisRecord ai = context.aiDiagnosis();
    ReportRcaRecord rca = context.rca();

    md.append("# 故障报告：").append(text(incident.title(), "未命名故障")).append("\n\n");

    renderMetadata(md, incident);
    renderSummary(md, incident, ai);
    renderImpact(md, context);
    renderTimeline(md, context.timeline());
    renderAlerts(md, context.alerts());
    renderEvidence(md, context.evidence());
    renderRca(md, incident, rca);
    renderAiDiagnosis(md, ai);
    renderSuggestions(md, ai);

    md.append("\n---\n\n");
    md.append("> 本报告由 AegisOps 自动生成，内容基于 Incident、Alert、Evidence、RCA 与 AI Diagnosis 快照。\n");

    return md.toString();
  }

  private void renderMetadata(StringBuilder md, ReportIncidentRecord incident) {
    md.append("## 基本信息\n\n");
    md.append("| 字段 | 值 |\n");
    md.append("| --- | --- |\n");
    row(md, "Incident ID", incident.id());
    row(md, "状态", incident.status());
    row(md, "级别", incident.severity());
    row(md, "来源", incident.source());
    row(md, "聚合键", incident.aggregationKey());
    row(md, "告警数", String.valueOf(incident.alertCount()));
    row(md, "开始时间", formatTime(incident.startedAt()));
    row(md, "发现时间", formatTime(incident.detectedAt()));
    row(md, "最后出现", formatTime(incident.lastSeenAt()));
    row(md, "恢复时间", formatTime(incident.resolvedAt()));
    md.append("\n");
  }

  private void renderSummary(
      StringBuilder md, ReportIncidentRecord incident, ReportAiDiagnosisRecord ai) {
    md.append("## 一、故障摘要\n\n");

    if (ai != null && notBlank(ai.summary())) {
      md.append(ai.summary()).append("\n\n");
      return;
    }

    if (notBlank(incident.summary())) {
      md.append(incident.summary()).append("\n\n");
      return;
    }

    md.append("系统检测到 ").append(text(incident.title(), "当前故障")).append("，需要结合告警、证据和诊断结果进一步分析。\n\n");
  }

  private void renderImpact(StringBuilder md, ReportContext context) {
    md.append("## 二、影响范围\n\n");

    if (context.aiDiagnosis() != null && notBlank(context.aiDiagnosis().impact())) {
      md.append(context.aiDiagnosis().impact()).append("\n\n");
    }

    List<String> entities =
        context.alerts().stream()
            .map(alert -> firstNonBlank(alert.entityName(), alert.assetId(), alert.sourceEventId()))
            .filter(this::notBlank)
            .distinct()
            .limit(10)
            .toList();

    if (entities.isEmpty()) {
      md.append("- 暂未识别明确影响对象\n\n");
      return;
    }

    for (String entity : entities) {
      md.append("- ").append(escapeInline(entity)).append("\n");
    }

    md.append("\n");
  }

  private void renderTimeline(StringBuilder md, List<ReportTimelineRecord> timeline) {
    md.append("## 三、时间线\n\n");

    if (timeline == null || timeline.isEmpty()) {
      md.append("暂无时间线记录。\n\n");
      return;
    }

    md.append("| 时间 | 类型 | 事件 | 说明 |\n");
    md.append("| --- | --- | --- | --- |\n");

    for (ReportTimelineRecord item : timeline) {
      md.append("| ")
          .append(escapeTable(formatTime(item.eventTime())))
          .append(" | ")
          .append(escapeTable(item.eventType()))
          .append(" | ")
          .append(escapeTable(item.title()))
          .append(" | ")
          .append(escapeTable(item.description()))
          .append(" |\n");
    }

    md.append("\n");
  }

  private void renderAlerts(StringBuilder md, List<ReportAlertRecord> alerts) {
    md.append("## 四、关联告警\n\n");

    if (alerts == null || alerts.isEmpty()) {
      md.append("暂无关联告警。\n\n");
      return;
    }

    md.append("| 时间 | 级别 | 状态 | 标题 | 对象 |\n");
    md.append("| --- | --- | --- | --- | --- |\n");

    for (ReportAlertRecord alert : alerts) {
      md.append("| ")
          .append(escapeTable(formatTime(alert.startsAt())))
          .append(" | ")
          .append(escapeTable(alert.severity()))
          .append(" | ")
          .append(escapeTable(alert.status()))
          .append(" | ")
          .append(escapeTable(alert.title()))
          .append(" | ")
          .append(escapeTable(firstNonBlank(alert.entityName(), alert.assetId(), "-")))
          .append(" |\n");
    }

    md.append("\n");
  }

  private void renderEvidence(StringBuilder md, List<ReportEvidenceRecord> evidence) {
    md.append("## 五、关键证据\n\n");

    if (evidence == null || evidence.isEmpty()) {
      md.append("暂无关键证据。\n\n");
      return;
    }

    int index = 1;
    for (ReportEvidenceRecord item : evidence) {
      md.append(index++).append(". **").append(escapeInline(item.title())).append("**");

      if (item.confidence() != null) {
        md.append("（confidence=").append(item.confidence()).append("）");
      }

      md.append("\n");
      md.append("   - 类型：").append(escapeInline(item.evidenceType())).append("\n");
      md.append("   - 摘要：").append(escapeInline(item.summary())).append("\n");
      md.append("   - 证据引用：`").append(escapeInline(item.evidenceKey())).append("`\n");

      if (item.timeRangeStart() != null || item.timeRangeEnd() != null) {
        md.append("   - 时间范围：")
            .append(formatTime(item.timeRangeStart()))
            .append(" ~ ")
            .append(formatTime(item.timeRangeEnd()))
            .append("\n");
      }
    }

    md.append("\n");
  }

  private void renderRca(StringBuilder md, ReportIncidentRecord incident, ReportRcaRecord rca) {
    md.append("## 六、RCA 根因判断\n\n");

    String suspectedRootCause =
        rca != null && notBlank(rca.suspectedRootCause())
            ? rca.suspectedRootCause()
            : incident.suspectedRootCause();

    if (!notBlank(suspectedRootCause)) {
      md.append("暂无 RCA 根因结论。\n\n");
      return;
    }

    md.append(suspectedRootCause).append("\n\n");

    BigDecimal confidence =
        rca != null && rca.confidence() != null ? rca.confidence() : incident.rcaConfidence();

    if (confidence != null) {
      md.append("- 置信度：").append(confidence).append("\n");
    }

    if (rca != null && notBlank(rca.summary())) {
      md.append("- RCA 摘要：").append(escapeInline(rca.summary())).append("\n");
    }

    List<String> matchedRules = extractRuleIds(rca == null ? null : rca.evidenceJson());
    if (!matchedRules.isEmpty()) {
      md.append("- 命中规则：").append(String.join(", ", matchedRules)).append("\n");
    }

    md.append("\n");
  }

  private void renderAiDiagnosis(StringBuilder md, ReportAiDiagnosisRecord ai) {
    md.append("## 七、AI 诊断\n\n");

    if (ai == null) {
      md.append("暂无 AI 诊断结果。\n\n");
      return;
    }

    if (notBlank(ai.summary())) {
      md.append("### 诊断结论\n\n").append(ai.summary()).append("\n\n");
    }

    if (notBlank(ai.rootCause())) {
      md.append("### 疑似根因\n\n").append(ai.rootCause()).append("\n\n");
    }

    List<String> matchedRules = readStringListFromRaw(ai.responseRawJson(), "matchedRules");
    List<String> evidenceRefs = readStringListFromRaw(ai.responseRawJson(), "evidenceRefs");

    if (!matchedRules.isEmpty()) {
      md.append("### 命中 RCA 规则\n\n");
      for (String rule : matchedRules) {
        md.append("- `").append(escapeInline(rule)).append("`\n");
      }
      md.append("\n");
    }

    if (!evidenceRefs.isEmpty()) {
      md.append("### 引用证据\n\n");
      for (String ref : evidenceRefs) {
        md.append("- `").append(escapeInline(ref)).append("`\n");
      }
      md.append("\n");
    }
  }

  private void renderSuggestions(StringBuilder md, ReportAiDiagnosisRecord ai) {
    md.append("## 八、处理建议\n\n");

    List<String> nextSteps = ai == null ? List.of() : readStringList(ai.nextStepsJson());

    if (nextSteps.isEmpty()) {
      md.append("1. 查看故障窗口内主机资源、服务日志和最近变更。\n");
      md.append("2. 确认是否存在发布、定时任务、流量突增或下游依赖异常。\n");
      md.append("3. 若服务持续不可用，优先进行扩容、限流或重启异常实例。\n\n");
      return;
    }

    int index = 1;
    for (String step : nextSteps) {
      md.append(index++).append(". ").append(step).append("\n");
    }

    List<String> runbooks = readStringList(ai.runbookSuggestionsJson());
    if (!runbooks.isEmpty()) {
      md.append("\n### Runbook 建议\n\n");
      for (String runbook : runbooks) {
        md.append("- ").append(escapeInline(runbook)).append("\n");
      }
    }

    md.append("\n");
  }

  private void row(StringBuilder md, String key, String value) {
    md.append("| ")
        .append(escapeTable(key))
        .append(" | ")
        .append(escapeTable(value))
        .append(" |\n");
  }

  private String formatTime(OffsetDateTime time) {
    return time == null ? "-" : TIME_FORMATTER.format(time);
  }

  private List<String> readStringList(String json) {
    try {
      if (json == null || json.isBlank()) {
        return List.of();
      }
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception ex) {
      return List.of();
    }
  }

  private List<String> readStringListFromRaw(String rawJson, String key) {
    try {
      if (rawJson == null || rawJson.isBlank()) {
        return List.of();
      }
      Map<String, Object> raw =
          objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {});
      Object value = raw.get(key);
      if (!(value instanceof Iterable<?> iterable)) {
        return List.of();
      }
      List<String> out = new ArrayList<>();
      for (Object item : iterable) {
        if (item != null && !String.valueOf(item).isBlank()) {
          out.add(String.valueOf(item).trim());
        }
      }
      return List.copyOf(out);
    } catch (Exception ex) {
      return List.of();
    }
  }

  private List<String> extractRuleIds(String evidenceJson) {
    try {
      if (evidenceJson == null || evidenceJson.isBlank()) {
        return List.of();
      }
      List<Map<String, Object>> evidence =
          objectMapper.readValue(evidenceJson, new TypeReference<List<Map<String, Object>>>() {});
      return evidence.stream()
          .map(item -> item.get("ruleId"))
          .filter(value -> value != null && !String.valueOf(value).isBlank())
          .map(String::valueOf)
          .distinct()
          .toList();
    } catch (Exception ex) {
      return List.of();
    }
  }

  private String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (notBlank(value)) {
        return value.trim();
      }
    }
    return null;
  }

  private String text(String value, String fallback) {
    return notBlank(value) ? value.trim() : fallback;
  }

  private boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }

  private String escapeInline(String value) {
    return value == null ? "" : value.replace("\n", " ").replace("\r", " ").trim();
  }

  private String escapeTable(String value) {
    return escapeInline(value).replace("|", "\\|");
  }
}

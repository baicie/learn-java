package io.aegisops.evidence;

import io.aegisops.zabbix.ZabbixHistoryPoint;
import io.aegisops.zabbix.ZabbixItem;
import io.aegisops.zabbix.ZabbixTrendPoint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ZabbixEvidenceAnalyzer {
  public Optional<DiagnosisEvidenceDraft> analyze(AnalysisQuery query) {
    ZabbixEvidenceSignal signal = ZabbixEvidenceSignal.classify(query.item());
    if (signal == ZabbixEvidenceSignal.UNKNOWN) {
      return Optional.empty();
    }

    DoubleSummaryStatistics stats = summarize(query.history(), query.trends());
    if (stats.getCount() == 0) {
      return Optional.empty();
    }

    MetricTemplate template = templateFor(signal);
    if (template == null || !template.matches(stats)) {
      return Optional.empty();
    }
    return Optional.of(template.draft(query, signal, stats));
  }

  private MetricTemplate templateFor(ZabbixEvidenceSignal signal) {
    switch (signal) {
      case CPU_UTILIZATION:
        return MetricTemplate.high("CPU 使用率持续高位", "CPU 使用率最大值 %.2f%%，平均值 %.2f%%", 80.0);
      case MEMORY_UTILIZATION:
        return MetricTemplate.high("内存使用率升高", "内存使用率最大值 %.2f%%，平均值 %.2f%%", 80.0);
      case LOAD_AVERAGE:
        return MetricTemplate.high("Load Average 升高", "Load Average 最大值 %.2f，平均值 %.2f", 4.0);
      case ORDER_CREATE_LATENCY:
        return MetricTemplate.high("接口响应时间明显升高", "接口响应时间最大值 %.2fs，平均值 %.2fs", 2.0);
      case HEALTH_STATUS:
        return MetricTemplate.low("健康检查失败", "健康状态最小值 %.2f，0 表示失败", 1.0);
      case ERROR_COUNT:
        return MetricTemplate.high("错误日志数量增加", "错误计数最大值 %.2f，平均值 %.2f", 0.0);
      default:
        return null;
    }
  }

  private DoubleSummaryStatistics summarize(
      List<ZabbixHistoryPoint> history, List<ZabbixTrendPoint> trends) {
    List<Double> values = numericValues(history, trends);
    return values.stream().mapToDouble(Double::doubleValue).summaryStatistics();
  }

  private List<Double> numericValues(
      List<ZabbixHistoryPoint> history, List<ZabbixTrendPoint> trends) {
    if (history != null && !history.isEmpty()) {
      return history.stream()
          .map(point -> point.doubleValue())
          .filter(value -> value.isPresent())
          .map(value -> value.getAsDouble())
          .toList();
    }

    if (trends != null && !trends.isEmpty()) {
      return trends.stream().map(point -> point.valueMax()).toList();
    }

    return List.of();
  }

  private static BigDecimal confidence(DoubleSummaryStatistics stats) {
    double score = stats.getCount() >= 3 ? 0.86 : 0.72;
    return BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
  }

  private static double round(double value) {
    return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
  }

  public record AnalysisQuery(
      String incidentId,
      String hostKey,
      ZabbixItem item,
      List<ZabbixHistoryPoint> history,
      List<ZabbixTrendPoint> trends,
      OffsetDateTime from,
      OffsetDateTime to) {}

  private record MetricTemplate(Mode mode, String title, String summaryTemplate, double threshold) {

    enum Mode {
      HIGH,
      LOW
    }

    static MetricTemplate high(String title, String summaryTemplate, double threshold) {
      return new MetricTemplate(Mode.HIGH, title, summaryTemplate, threshold);
    }

    static MetricTemplate low(String title, String summaryTemplate, double expectedMinimum) {
      return new MetricTemplate(Mode.LOW, title, summaryTemplate, expectedMinimum);
    }

    boolean matches(DoubleSummaryStatistics stats) {
      return mode == Mode.HIGH ? stats.getMax() > threshold : stats.getMin() < threshold;
    }

    String renderSummary(DoubleSummaryStatistics stats) {
      return mode == Mode.HIGH
          ? summaryTemplate.formatted(stats.getMax(), stats.getAverage())
          : summaryTemplate.formatted(stats.getMin());
    }

    DiagnosisEvidenceDraft draft(
        AnalysisQuery query, ZabbixEvidenceSignal signal, DoubleSummaryStatistics stats) {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("incidentId", query.incidentId());
      payload.put("hostKey", query.hostKey());
      payload.put("itemId", query.item().itemId());
      payload.put("itemName", query.item().name());
      payload.put("itemKey", query.item().key());
      payload.put("signal", signal.name());
      payload.put("threshold", threshold);
      payload.put("min", round(stats.getMin()));
      payload.put("max", round(stats.getMax()));
      payload.put("avg", round(stats.getAverage()));
      payload.put("count", stats.getCount());

      return new DiagnosisEvidenceDraft(
          "zabbix:" + signal.name().toLowerCase() + ":" + query.item().itemId(),
          "zabbix",
          signal.evidenceType(),
          title,
          renderSummary(stats),
          query.from(),
          query.to(),
          confidence(stats),
          payload);
    }
  }
}

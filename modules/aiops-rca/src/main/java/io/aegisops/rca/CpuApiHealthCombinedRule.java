package io.aegisops.rca;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Highest-confidence combined rule: fires only when CPU-high, API-slow, and health-check-failed all
 * appear in the same fault window.
 */
@Component
public class CpuApiHealthCombinedRule implements RcaRule {
  public static final String RULE_ID = "CPU_API_HEALTH_COMBINED";

  @Override
  public String id() {
    return RULE_ID;
  }

  @Override
  public RcaRuleResult evaluate(RcaAnalysisContext context) {
    RcaEvidenceIndex index = context.evidenceIndex();

    if (!index.has("metric_cpu_high")
        || !index.has("metric_api_slow")
        || !index.has("metric_health_check_failed")) {
      return RcaRuleResult.none(RULE_ID);
    }

    List<RcaDiagnosisEvidenceRecord> refs =
        index.getAny(
            "metric_cpu_high",
            "metric_api_slow",
            "metric_health_check_failed",
            "metric_error_log_increased",
            "zabbix_event_timeline",
            "zabbix_trigger_expression");

    BigDecimal score = new BigDecimal("0.92");
    BigDecimal confidence = new BigDecimal("0.88");

    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("evidenceRefs", RcaEvidenceFactory.evidenceRefs(refs));
    attributes.put("evidenceTypes", RcaEvidenceFactory.evidenceTypes(refs));
    attributes.put("source", "diagnosis_evidence");

    RcaEvidence evidence =
        new RcaEvidence(
            RULE_ID,
            "CPU 高位、接口慢、健康检查失败同时出现",
            "同一故障窗口内同时存在 CPU 使用率高位、接口响应变慢和健康检查失败，符合主机资源耗尽导致服务不可用的组合特征。",
            score,
            confidence,
            attributes);

    return new RcaRuleResult(
        RULE_ID, "主机 CPU 持续高位导致服务响应变慢，并进一步引发健康检查失败", score, confidence, List.of(evidence));
  }
}

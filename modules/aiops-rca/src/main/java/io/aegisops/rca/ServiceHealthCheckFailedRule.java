package io.aegisops.rca;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Matches when health-check evidence is present, optionally combined with API-slow. */
@Component
public class ServiceHealthCheckFailedRule implements RcaRule {
  public static final String RULE_ID = "SERVICE_HEALTH_CHECK_FAILED";

  @Override
  public String id() {
    return RULE_ID;
  }

  @Override
  public RcaRuleResult evaluate(RcaAnalysisContext context) {
    RcaEvidenceIndex index = context.evidenceIndex();

    if (!index.has("metric_health_check_failed")) {
      return RcaRuleResult.none(RULE_ID);
    }

    boolean apiSlow = index.has("metric_api_slow");
    BigDecimal score = apiSlow ? new BigDecimal("0.78") : new BigDecimal("0.68");
    BigDecimal confidence = apiSlow ? new BigDecimal("0.80") : new BigDecimal("0.70");

    List<RcaDiagnosisEvidenceRecord> refs =
        index.getAny("metric_health_check_failed", "metric_api_slow", "zabbix_event_timeline");

    String rootCause = apiSlow ? "服务健康检查失败，并伴随接口响应变慢" : "服务健康检查失败，服务可用性异常";

    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("evidenceRefs", RcaEvidenceFactory.evidenceRefs(refs));
    attributes.put("evidenceTypes", RcaEvidenceFactory.evidenceTypes(refs));
    attributes.put("source", "diagnosis_evidence");
    attributes.put("apiSlow", apiSlow);

    RcaEvidence evidence =
        new RcaEvidence(
            RULE_ID,
            "服务健康检查失败",
            "Zabbix 证据显示健康检查失败。若同时存在接口慢，说明服务已经影响对外可用性。",
            score,
            confidence,
            attributes);

    return new RcaRuleResult(RULE_ID, rootCause, score, confidence, List.of(evidence));
  }
}

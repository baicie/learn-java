package io.aegisops.rca;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/** Matches when both CPU-high and API-slow diagnosis evidence are present. */
@Component
public class HostCpuHighWithServiceSlowRule implements RcaRule {
  public static final String RULE_ID = "HOST_CPU_HIGH_WITH_SERVICE_SLOW";

  @Override
  public String id() {
    return RULE_ID;
  }

  @Override
  public RcaRuleResult evaluate(RcaAnalysisContext context) {
    RcaEvidenceIndex index = context.evidenceIndex();

    if (!index.has("metric_cpu_high") || !index.has("metric_api_slow")) {
      return RcaRuleResult.none(RULE_ID);
    }

    List<RcaDiagnosisEvidenceRecord> refs =
        index.getAny("metric_cpu_high", "metric_api_slow", "zabbix_trigger_expression");

    BigDecimal score = new BigDecimal("0.82");
    BigDecimal confidence = new BigDecimal("0.82");

    RcaEvidence evidence =
        RcaEvidenceFactory.fromDiagnosisEvidence(
            RULE_ID, "主机资源异常与服务响应变慢相关", score, confidence, refs);

    return new RcaRuleResult(RULE_ID, "主机 CPU 持续高位导致服务响应变慢", score, confidence, List.of(evidence));
  }
}

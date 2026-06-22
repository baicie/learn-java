package io.aegisops.rca;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Matches when error-log-increased evidence is present, with extra weight for "timeout". */
@Component
public class ErrorLogIncreasedRule implements RcaRule {
  public static final String RULE_ID = "ERROR_LOG_INCREASED_WITH_TIMEOUT";

  @Override
  public String id() {
    return RULE_ID;
  }

  @Override
  public RcaRuleResult evaluate(RcaAnalysisContext context) {
    RcaEvidenceIndex index = context.evidenceIndex();

    if (!index.has("metric_error_log_increased")) {
      return RcaRuleResult.none(RULE_ID);
    }

    boolean timeout =
        index.containsText("metric_error_log_increased", "timeout")
            || index.containsText("zabbix_event_timeline", "timeout");

    BigDecimal score = timeout ? new BigDecimal("0.76") : new BigDecimal("0.62");
    BigDecimal confidence = timeout ? new BigDecimal("0.78") : new BigDecimal("0.66");

    List<RcaDiagnosisEvidenceRecord> refs =
        index.getAny("metric_error_log_increased", "zabbix_event_timeline");

    String rootCause = timeout ? "服务错误日志增加并出现 Timeout，疑似请求处理阻塞或下游调用超时" : "服务错误日志数量增加，疑似服务内部异常";
    String description =
        timeout ? "错误计数增加且证据中出现 Timeout，说明服务请求处理或下游调用存在超时风险。" : "错误计数增加，说明故障窗口内服务内部异常增多。";

    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("evidenceRefs", RcaEvidenceFactory.evidenceRefs(refs));
    attributes.put("evidenceTypes", RcaEvidenceFactory.evidenceTypes(refs));
    attributes.put("source", "diagnosis_evidence");
    attributes.put("timeout", timeout);

    RcaEvidence evidence =
        new RcaEvidence(RULE_ID, "错误日志数量增加", description, score, confidence, attributes);

    return new RcaRuleResult(RULE_ID, rootCause, score, confidence, List.of(evidence));
  }
}

package io.aegisops.evidence;

import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.MetricEvidence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "aiops.evidence.victoria",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class NoopMetricsEvidenceClient implements MetricsEvidenceClient {
  @Override
  public MetricEvidence queryMetrics(EvidenceQueryRequest request) {
    return MetricEvidence.unavailable("VictoriaMetrics evidence client is not enabled.");
  }
}

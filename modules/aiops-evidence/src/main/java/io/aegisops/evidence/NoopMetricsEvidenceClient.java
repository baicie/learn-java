package io.aegisops.evidence;

import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.MetricEvidence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(MetricsEvidenceClient.class)
public class NoopMetricsEvidenceClient implements MetricsEvidenceClient {
  @Override
  public MetricEvidence queryMetrics(EvidenceQueryRequest request) {
    return MetricEvidence.unavailable("VictoriaMetrics evidence client is not enabled.");
  }
}

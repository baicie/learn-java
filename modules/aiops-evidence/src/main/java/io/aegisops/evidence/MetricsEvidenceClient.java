package io.aegisops.evidence;

import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.MetricEvidence;

public interface MetricsEvidenceClient {
  MetricEvidence queryMetrics(EvidenceQueryRequest request);
}

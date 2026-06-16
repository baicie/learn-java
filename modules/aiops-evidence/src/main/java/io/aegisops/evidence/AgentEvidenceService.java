package io.aegisops.evidence;

import io.aegisops.evidence.dto.ChangeEvidence;
import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.EvidenceQueryResponse;
import io.aegisops.evidence.dto.LogEvidence;
import io.aegisops.evidence.dto.MetricEvidence;
import java.time.OffsetDateTime;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
@EnableConfigurationProperties({AgentEvidenceProperties.class, VictoriaMetricsProperties.class})
public class AgentEvidenceService {
  private final AgentEvidenceProperties properties;
  private final MetricsEvidenceClient metricsClient;
  private final EvidenceRepository repository;

  public AgentEvidenceService(
      AgentEvidenceProperties properties,
      MetricsEvidenceClient metricsClient,
      EvidenceRepository repository) {
    this.properties = properties;
    this.metricsClient = metricsClient;
    this.repository = repository;
  }

  public EvidenceQueryResponse query(EvidenceQueryRequest request) {
    EvidenceQueryRequest normalized = normalizeWindow(request);

    MetricEvidence metrics = metricsClient.queryMetrics(normalized);
    LogEvidence logs = repository.queryLogs(normalized, properties.normalizedMaxLogPatterns());
    ChangeEvidence changes = repository.queryChanges(normalized, properties.normalizedMaxChanges());

    return new EvidenceQueryResponse(
        normalized.contractVersion(),
        normalized.tenantId(),
        normalized.incidentId(),
        normalized.traceId(),
        metrics,
        logs,
        changes);
  }

  private EvidenceQueryRequest normalizeWindow(EvidenceQueryRequest request) {
    OffsetDateTime end = request.lastSeenAt() == null ? OffsetDateTime.now() : request.lastSeenAt();
    OffsetDateTime start =
        request.startedAt() == null
            ? end.minusMinutes(properties.normalizedDefaultLookbackMinutes())
            : request.startedAt();

    return new EvidenceQueryRequest(
        request.contractVersion(),
        request.tenantId(),
        request.incidentId(),
        request.traceId(),
        request.primaryAssetId(),
        start,
        end,
        request.normalizedAlertFingerprints(),
        request.normalizedAlertTitles());
  }
}

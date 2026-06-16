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

    MetricEvidence metrics = safeMetrics(normalized);
    LogEvidence logs = safeLogs(normalized);
    ChangeEvidence changes = safeChanges(normalized);

    return new EvidenceQueryResponse(
        normalized.contractVersion(),
        normalized.tenantId(),
        normalized.incidentId(),
        normalized.traceId(),
        metrics,
        logs,
        changes);
  }

  private MetricEvidence safeMetrics(EvidenceQueryRequest request) {
    try {
      return metricsClient.queryMetrics(request);
    } catch (Exception ex) {
      return MetricEvidence.unavailable(
          "Metric evidence query failed: " + ex.getClass().getSimpleName());
    }
  }

  private LogEvidence safeLogs(EvidenceQueryRequest request) {
    try {
      return repository.queryLogs(request, properties.normalizedMaxLogPatterns());
    } catch (Exception ex) {
      return LogEvidence.unavailable(
          "Log evidence query failed: " + ex.getClass().getSimpleName());
    }
  }

  private ChangeEvidence safeChanges(EvidenceQueryRequest request) {
    try {
      return repository.queryChanges(request, properties.normalizedMaxChanges());
    } catch (Exception ex) {
      return ChangeEvidence.unavailable(
          "Change evidence query failed: " + ex.getClass().getSimpleName());
    }
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
        request.normalizedAlertTitles(),
        request.normalizedServiceNames());
  }
}

package io.aegisops.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
  private final ReportRepository repository;
  private final MarkdownReportRenderer renderer;
  private final ObjectMapper objectMapper;

  public ReportService(
      ReportRepository repository, MarkdownReportRenderer renderer, ObjectMapper objectMapper) {
    this.repository = repository;
    this.renderer = renderer;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public IncidentReportResponse generate(
      String tenantId, String incidentId, GenerateIncidentReportRequest request) {
    GenerateIncidentReportRequest normalized =
        request == null ? new GenerateIncidentReportRequest(true, "zh-CN", "system") : request;

    if (!normalized.shouldGenerate()) {
      return repository
          .findLatestReport(tenantId, incidentId)
          .map(IncidentReportResponse::from)
          .orElseGet(
              () ->
                  generate(
                      tenantId,
                      incidentId,
                      new GenerateIncidentReportRequest(
                          true, normalized.normalizedLocale(), normalized.normalizedCreatedBy())));
    }

    ReportContext context = buildContext(tenantId, incidentId, normalized.normalizedLocale());
    String markdown = renderer.render(context);
    String snapshotJson = writeSnapshot(context);

    int versionNo = repository.nextVersionNo(tenantId, incidentId);
    String title = "故障报告：" + context.incident().title();

    IncidentReportRecord report =
        repository.insertReport(
            new InsertReportParams(
                newId("rpt"),
                tenantId,
                incidentId,
                versionNo,
                title,
                markdown,
                snapshotJson,
                normalized.normalizedCreatedBy()));

    return IncidentReportResponse.from(report);
  }

  public IncidentReportResponse latest(String tenantId, String incidentId) {
    ensureIncidentExists(tenantId, incidentId);

    return repository
        .findLatestReport(tenantId, incidentId)
        .map(IncidentReportResponse::from)
        .orElseThrow(
            () -> new AppException("INCIDENT_REPORT_NOT_FOUND", "Incident report not found"));
  }

  private ReportContext buildContext(String tenantId, String incidentId, String locale) {
    ReportIncidentRecord incident =
        repository
            .findIncident(tenantId, incidentId)
            .orElseThrow(() -> new AppException("INCIDENT_NOT_FOUND", "Incident not found"));

    return new ReportContext(
        incident,
        repository.listIncidentAlerts(tenantId, incidentId),
        repository.listEvidence(tenantId, incidentId),
        repository.findLatestRca(tenantId, incidentId).orElse(null),
        repository.findLatestAiDiagnosis(tenantId, incidentId).orElse(null),
        repository.listTimeline(tenantId, incidentId, 100),
        locale);
  }

  private void ensureIncidentExists(String tenantId, String incidentId) {
    repository
        .findIncident(tenantId, incidentId)
        .orElseThrow(() -> new AppException("INCIDENT_NOT_FOUND", "Incident not found"));
  }

  private String writeSnapshot(ReportContext context) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("incidentId", context.incident().id());
    snapshot.put("generatedAt", OffsetDateTime.now().toString());
    snapshot.put("locale", context.locale());
    snapshot.put("alertIds", context.alerts().stream().map(ReportAlertRecord::id).toList());
    snapshot.put(
        "evidenceKeys",
        context.evidence().stream().map(ReportEvidenceRecord::evidenceKey).toList());
    snapshot.put("rcaId", context.rca() == null ? null : context.rca().id());
    snapshot.put(
        "aiDiagnosisId", context.aiDiagnosis() == null ? null : context.aiDiagnosis().id());
    snapshot.put("timelineIds", context.timeline().stream().map(ReportTimelineRecord::id).toList());

    try {
      return objectMapper.writeValueAsString(snapshot);
    } catch (JsonProcessingException ex) {
      throw new AppException(
          "REPORT_SNAPSHOT_SERIALIZE_FAILED", "Failed to serialize report snapshot");
    }
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}

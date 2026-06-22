package io.aegisops.report;

import java.util.List;
import java.util.Optional;

public interface ReportRepository {
  Optional<ReportIncidentRecord> findIncident(String tenantId, String incidentId);

  List<ReportAlertRecord> listIncidentAlerts(String tenantId, String incidentId);

  List<ReportEvidenceRecord> listEvidence(String tenantId, String incidentId);

  Optional<ReportRcaRecord> findLatestRca(String tenantId, String incidentId);

  Optional<ReportAiDiagnosisRecord> findLatestAiDiagnosis(String tenantId, String incidentId);

  List<ReportTimelineRecord> listTimeline(String tenantId, String incidentId, int limit);

  Optional<IncidentReportRecord> findLatestReport(String tenantId, String incidentId);

  int nextVersionNo(String tenantId, String incidentId);

  IncidentReportRecord insertReport(InsertReportParams params);
}

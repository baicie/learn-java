package io.aegisops.report;

import java.util.List;

public record ReportContext(
    ReportIncidentRecord incident,
    List<ReportAlertRecord> alerts,
    List<ReportEvidenceRecord> evidence,
    ReportRcaRecord rca,
    ReportAiDiagnosisRecord aiDiagnosis,
    List<ReportTimelineRecord> timeline,
    String locale) {}

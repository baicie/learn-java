package io.aegisops.rca;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class RcaTestFixtures {
  private RcaTestFixtures() {}

  public static RcaAnalysisContext contextWithEvidence(String... evidenceTypes) {
    RcaDiagnosisEvidenceRecord[] evidence = new RcaDiagnosisEvidenceRecord[evidenceTypes.length];
    for (int i = 0; i < evidenceTypes.length; i++) {
      String type = evidenceTypes[i];
      evidence[i] = evidence("evd_" + type, type, type, type);
    }
    return contextWithEvidence(evidence);
  }

  public static RcaAnalysisContext contextWithEvidence(RcaDiagnosisEvidenceRecord... evidence) {
    return new RcaAnalysisContext(
        incident(), List.of(), List.of(), evidence == null ? List.of() : List.of(evidence));
  }

  public static RcaDiagnosisEvidenceRecord evidence(
      String evidenceKey, String evidenceType, String title, String summary) {
    return new RcaDiagnosisEvidenceRecord(
        "id_" + evidenceKey,
        "inc_1",
        evidenceKey,
        "zabbix",
        evidenceType,
        title,
        summary,
        OffsetDateTime.parse("2026-06-21T05:00:00Z"),
        OffsetDateTime.parse("2026-06-21T05:30:00Z"),
        BigDecimal.valueOf(0.86),
        "{ \"summary\": \"" + summary + "\" }");
  }

  public static RcaAnalysisContext contextWithAlerts(RcaAlertRecord... alerts) {
    return new RcaAnalysisContext(incident(), List.of(alerts), List.of(), List.of());
  }

  public static RcaAnalysisContext contextWithAlerts(List<RcaAlertRecord> alerts) {
    return new RcaAnalysisContext(incident(), alerts, List.of(), List.of());
  }

  private static List<RcaAlertRecord> alertRecords(List<AlertParams> alerts) {
    return alerts.stream().map(name -> RcaTestFixtures.alert(name)).toList();
  }

  public static RcaAlertRecord alert(AlertParams params) {
    return new RcaAlertRecord(
        params.id(),
        "zabbix",
        params.id(),
        params.severity(),
        params.title(),
        null,
        params.assetId(),
        null,
        null,
        params.fingerprint(),
        null,
        OffsetDateTime.now().minusMinutes(params.minuteOffset()),
        OffsetDateTime.now().minusMinutes(params.minuteOffset()));
  }

  public static RcaAssetRelationRecord relation() {
    return new RcaAssetRelationRecord(
        "rel_1", "asset_1", "asset_2", "depends_on", BigDecimal.ONE, "zabbix");
  }

  public static RcaIncidentRecord incident() {
    return new RcaIncidentRecord(
        "inc_1",
        "tenant_1",
        "order-service 主机与服务异常",
        null,
        null,
        null,
        null,
        null,
        null,
        0,
        null,
        null,
        OffsetDateTime.parse("2026-06-21T05:00:00Z"),
        null,
        OffsetDateTime.parse("2026-06-21T05:30:00Z"),
        null,
        OffsetDateTime.parse("2026-06-21T05:00:00Z"),
        OffsetDateTime.parse("2026-06-21T05:30:00Z"));
  }
}

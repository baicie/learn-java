package io.aegisops.rca;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class RcaTestFixtures {
  private RcaTestFixtures() {}

  public static RcaIncidentRecord incident() {
    OffsetDateTime now = OffsetDateTime.parse("2026-06-14T10:00:00+09:00");

    return new RcaIncidentRecord(
        "inc_1",
        "tenant_1",
        "CPU high",
        "summary",
        "critical",
        "open",
        "system",
        "asset_1",
        "zabbix:fp_cpu",
        3,
        null,
        null,
        now.minusMinutes(5),
        now,
        now,
        null,
        now,
        now);
  }

  public static RcaAlertRecord alert(AlertParams params) {
    OffsetDateTime base = OffsetDateTime.parse("2026-06-14T10:00:00+09:00");

    return new RcaAlertRecord(
        params.id(),
        "zabbix",
        "source_" + params.id(),
        params.severity(),
        params.title(),
        "description " + params.id(),
        params.assetId(),
        "host",
        "host-1",
        params.fingerprint(),
        "{}",
        base.plusMinutes(params.minuteOffset()),
        base.plusMinutes(params.minuteOffset()));
  }

  public static RcaAnalysisContext contextWithAlerts(List<RcaAlertRecord> alerts) {
    return new RcaAnalysisContext(incident(), alerts, List.of());
  }

  public static RcaAssetRelationRecord relation() {
    return new RcaAssetRelationRecord(
        "rel_1", "asset_1", "asset_2", "depends_on", new BigDecimal("0.9000"), "manual");
  }
}

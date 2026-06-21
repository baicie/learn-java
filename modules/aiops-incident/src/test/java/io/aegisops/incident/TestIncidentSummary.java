package io.aegisops.incident;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Test helper that constructs an IncidentSummaryRecord with minimal parameters. */
public record TestIncidentSummary(SummaryParams params) {
  private static final OffsetDateTime FIXED_TIME =
      OffsetDateTime.parse("2026-06-14T10:00:00+09:00");

  public IncidentSummaryRecord toSummary() {
    OffsetDateTime resolvedAt =
        List.of("resolved", "closed", "ignored").contains(params.status()) ? FIXED_TIME : null;
    return new IncidentSummaryRecord(
        params.id(),
        params.tenantId(),
        params.title(),
        "summary",
        params.severity(),
        params.status(),
        "system",
        "asset_1",
        params.aggregationKey(),
        params.alertCount(),
        BigDecimal.ZERO,
        FIXED_TIME,
        FIXED_TIME,
        FIXED_TIME,
        resolvedAt,
        FIXED_TIME,
        FIXED_TIME);
  }
}

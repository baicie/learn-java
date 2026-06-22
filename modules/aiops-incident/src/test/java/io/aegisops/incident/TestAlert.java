package io.aegisops.incident;

import java.time.OffsetDateTime;

/**
 * Test helper that maps from 2 meaningful fields to a fully-populated AlertCandidate. Defaults the
 * remaining 12 fields so test helpers only need to specify id and the one field under test.
 */
public record TestAlert(String id, String fingerprint) {
  private static final OffsetDateTime FIXED_TIME =
      OffsetDateTime.parse("2026-06-14T10:00:00+09:00");

  public AlertCandidate toCandidate() {
    return new AlertCandidate(
        id,
        "tenant_1",
        "zabbix",
        "source_" + id,
        "warning",
        "Alert " + id,
        "desc " + id,
        "asset_1",
        "host",
        "host-1",
        fingerprint,
        fingerprint,
        "{}",
        FIXED_TIME,
        null,
        FIXED_TIME);
  }
}

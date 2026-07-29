package io.aegisops.alert;

/** Result of an alert ingest upsert operation. */
public record AlertIngestResult(
    String alertId, boolean created, String status, String fingerprint, String aggregationKey) {
  public AlertIngestResult(
      String alertId, boolean created, String fingerprint, String aggregationKey) {
    this(alertId, created, "open", fingerprint, aggregationKey);
  }
}

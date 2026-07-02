package io.aegisops.alert;

/** Result of an alert ingest upsert operation. */
public record AlertIngestResult(
    String alertId, boolean created, String fingerprint, String aggregationKey) {}

package io.aegisops.rca;

/** DTO for test alert fixture parameters. */
public record AlertParams(
    String id,
    String severity,
    String title,
    String assetId,
    String fingerprint,
    int minuteOffset) {}

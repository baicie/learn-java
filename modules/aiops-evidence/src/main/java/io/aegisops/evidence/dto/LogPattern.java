package io.aegisops.evidence.dto;

import java.time.OffsetDateTime;

public record LogPattern(
    String severity,
    String sample,
    long count,
    OffsetDateTime firstSeenAt,
    OffsetDateTime lastSeenAt) {}

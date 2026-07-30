package io.aegisops.common.security;

import java.time.Instant;

public record DiagnosisGrantClaims(
    String issuer,
    String audience,
    String tenantId,
    String incidentId,
    String traceId,
    Instant issuedAt,
    Instant expiresAt) {}

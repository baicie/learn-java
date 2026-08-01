package io.aegisops.common.security;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record DiagnosisGrantClaims(
    String issuer,
    String subject,
    Set<String> audiences,
    List<String> scopes,
    String tenantId,
    String incidentId,
    String diagnosisId,
    String traceId,
    Instant issuedAt,
    Instant expiresAt,
    String jti) {
  public DiagnosisGrantClaims {
    audiences = audiences == null ? Set.of() : Set.copyOf(audiences);
    scopes = scopes == null ? List.of() : List.copyOf(scopes);
  }
}

package io.aegisops.common.security;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record ExecutionGrantClaims(
    String issuer,
    String subject,
    Set<String> audiences,
    List<String> scopes,
    String tenantId,
    String incidentId,
    String executionId,
    String planId,
    String mode,
    String executionKind,
    String rollbackPlanId,
    String rollbackOfExecutionId,
    String snapshotSha256,
    int maxDurationSeconds,
    Instant issuedAt,
    Instant expiresAt,
    String jti) {
  public ExecutionGrantClaims {
    audiences = audiences == null ? Set.of() : Set.copyOf(audiences);
    scopes = scopes == null ? List.of() : List.copyOf(scopes);
  }
}

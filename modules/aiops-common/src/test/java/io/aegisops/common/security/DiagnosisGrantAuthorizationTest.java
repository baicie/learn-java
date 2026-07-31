package io.aegisops.common.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DiagnosisGrantAuthorizationTest {
  @Test
  void authorizesOnlyTheGrantedTenant() {
    assertEquals("tenant_1", DiagnosisGrantAuthorization.requireTenant(grant(), "tenant_1"));
    assertThrows(
        SecurityException.class,
        () -> DiagnosisGrantAuthorization.requireTenant(grant(), "tenant_2"));
  }

  @Test
  void authorizesOnlyTheGrantedDiagnosisContext() {
    assertDoesNotThrow(
        () ->
            DiagnosisGrantAuthorization.requireContext(
                grant(), "tenant_1", "incident_1", "trace_1"));
    assertThrows(
        SecurityException.class,
        () ->
            DiagnosisGrantAuthorization.requireContext(
                grant(), "tenant_1", "incident_2", "trace_1"));
    assertThrows(
        SecurityException.class,
        () ->
            DiagnosisGrantAuthorization.requireContext(
                grant(), "tenant_1", "incident_1", "trace_2"));
  }

  private DiagnosisGrantClaims grant() {
    return new DiagnosisGrantClaims(
        "aiops-server",
        "aegisops-internal-api",
        "tenant_1",
        "incident_1",
        "trace_1",
        Instant.parse("2026-07-30T08:00:00Z"),
        Instant.parse("2026-07-30T08:05:00Z"));
  }
}

package io.aegisops.common.security;

import java.util.Objects;

public final class DiagnosisGrantAuthorization {
  public static final String REQUEST_ATTRIBUTE = "io.aegisops.security.diagnosisGrant";

  private static final String ACCESS_DENIED = "Diagnosis grant does not authorize this request";

  private DiagnosisGrantAuthorization() {}

  public static String requireTenant(DiagnosisGrantClaims claims, String tenantId) {
    if (claims == null || !Objects.equals(claims.tenantId(), tenantId)) {
      throw new SecurityException(ACCESS_DENIED);
    }
    return claims.tenantId();
  }

  public static void requireContext(
      DiagnosisGrantClaims claims, String tenantId, String incidentId, String traceId) {
    requireIncident(claims, tenantId, incidentId);
    if (!Objects.equals(claims.traceId(), traceId)) {
      throw new SecurityException(ACCESS_DENIED);
    }
  }

  public static void requireIncident(
      DiagnosisGrantClaims claims, String tenantId, String incidentId) {
    requireTenant(claims, tenantId);
    if (!Objects.equals(claims.incidentId(), incidentId)) {
      throw new SecurityException(ACCESS_DENIED);
    }
  }

  public static void requireScope(DiagnosisGrantClaims claims, String scope) {
    if (claims == null || !claims.scopes().contains(scope)) {
      throw new SecurityException(ACCESS_DENIED);
    }
  }

  public static void requireDiagnosis(DiagnosisGrantClaims claims, String diagnosisId) {
    if (claims == null || !Objects.equals(claims.diagnosisId(), diagnosisId)) {
      throw new SecurityException(ACCESS_DENIED);
    }
  }
}

package io.aegisops.security;

import io.aegisops.common.security.DiagnosisGrantAuthorization;

public final class SecurityConstants {
  public static final String HEADER_TENANT_ID = "X-Tenant-Id";
  public static final String HEADER_DIAGNOSIS_GRANT = "X-AegisOps-Diagnosis-Grant";
  public static final String REQUEST_ATTRIBUTE_DIAGNOSIS_GRANT =
      DiagnosisGrantAuthorization.REQUEST_ATTRIBUTE;
  public static final String REQUEST_ATTRIBUTE_SERVICE_PRINCIPAL =
      "io.aegisops.security.servicePrincipal";

  private SecurityConstants() {}
}

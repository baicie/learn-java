package io.aegisops.security;

public final class SecurityConstants {
  public static final String HEADER_TENANT_ID = "X-Tenant-Id";
  public static final String HEADER_INTERNAL_AGENT_TOKEN = "X-AIOPS-INTERNAL-TOKEN";
  public static final String HEADER_DIAGNOSIS_GRANT = "X-AegisOps-Diagnosis-Grant";
  public static final String REQUEST_ATTRIBUTE_DIAGNOSIS_GRANT =
      "io.aegisops.security.diagnosisGrant";
  public static final String REQUEST_ATTRIBUTE_SERVICE_PRINCIPAL =
      "io.aegisops.security.servicePrincipal";

  private SecurityConstants() {}
}

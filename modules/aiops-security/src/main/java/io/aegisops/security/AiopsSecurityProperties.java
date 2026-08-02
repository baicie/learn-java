package io.aegisops.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.security")
public class AiopsSecurityProperties {
  private String diagnosisGrantAudience = "aegisops-internal-api";
  private List<String> diagnosisGrantIssuers = List.of("aegisops-app");
  private List<String> internalAgentCertificateIdentities =
      List.of("spiffe://aegisops.local/service/aiops-agent");
  private String diagnosisGrantKeyId = "task-grant-v1";
  private String diagnosisGrantPublicKeyFile = "";
  private String diagnosisGrantPreviousKeyId = "";
  private String diagnosisGrantPreviousPublicKeyFile = "";
  private boolean tenantRequired = true;
  private List<String> allowedOrigins = List.of("http://localhost:5173", "http://127.0.0.1:5173");

  public String getDiagnosisGrantAudience() {
    return diagnosisGrantAudience;
  }

  public void setDiagnosisGrantAudience(String diagnosisGrantAudience) {
    this.diagnosisGrantAudience = diagnosisGrantAudience;
  }

  public List<String> getDiagnosisGrantIssuers() {
    return diagnosisGrantIssuers;
  }

  public void setDiagnosisGrantIssuers(List<String> diagnosisGrantIssuers) {
    this.diagnosisGrantIssuers =
        diagnosisGrantIssuers == null ? List.of() : List.copyOf(diagnosisGrantIssuers);
  }

  public List<String> getInternalAgentCertificateIdentities() {
    return internalAgentCertificateIdentities;
  }

  public void setInternalAgentCertificateIdentities(
      List<String> internalAgentCertificateIdentities) {
    this.internalAgentCertificateIdentities =
        internalAgentCertificateIdentities == null
            ? List.of()
            : List.copyOf(internalAgentCertificateIdentities);
  }

  public String getDiagnosisGrantKeyId() {
    return diagnosisGrantKeyId;
  }

  public void setDiagnosisGrantKeyId(String diagnosisGrantKeyId) {
    this.diagnosisGrantKeyId = diagnosisGrantKeyId;
  }

  public String getDiagnosisGrantPublicKeyFile() {
    return diagnosisGrantPublicKeyFile;
  }

  public void setDiagnosisGrantPublicKeyFile(String diagnosisGrantPublicKeyFile) {
    this.diagnosisGrantPublicKeyFile = diagnosisGrantPublicKeyFile;
  }

  public String getDiagnosisGrantPreviousKeyId() {
    return diagnosisGrantPreviousKeyId;
  }

  public void setDiagnosisGrantPreviousKeyId(String diagnosisGrantPreviousKeyId) {
    this.diagnosisGrantPreviousKeyId = diagnosisGrantPreviousKeyId;
  }

  public String getDiagnosisGrantPreviousPublicKeyFile() {
    return diagnosisGrantPreviousPublicKeyFile;
  }

  public void setDiagnosisGrantPreviousPublicKeyFile(String diagnosisGrantPreviousPublicKeyFile) {
    this.diagnosisGrantPreviousPublicKeyFile = diagnosisGrantPreviousPublicKeyFile;
  }

  public boolean isTenantRequired() {
    return tenantRequired;
  }

  public void setTenantRequired(boolean tenantRequired) {
    this.tenantRequired = tenantRequired;
  }

  public List<String> getAllowedOrigins() {
    return allowedOrigins;
  }

  public void setAllowedOrigins(List<String> allowedOrigins) {
    this.allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
  }

  void validateDiagnosisGrant() {
    requireText(diagnosisGrantAudience, "aiops.security.diagnosis-grant-audience is required");
    if (diagnosisGrantIssuers.isEmpty()
        || diagnosisGrantIssuers.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new IllegalStateException(
          "aiops.security.diagnosis-grant-issuers must contain non-blank issuers");
    }
  }

  void validateInternalAgentCertificateIdentities() {
    if (internalAgentCertificateIdentities.isEmpty()
        || internalAgentCertificateIdentities.stream()
            .anyMatch(value -> value == null || value.isBlank())) {
      throw new IllegalStateException(
          "aiops.security.internal-agent-certificate-identities must contain non-blank identities");
    }
  }

  private static void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(message);
    }
  }
}

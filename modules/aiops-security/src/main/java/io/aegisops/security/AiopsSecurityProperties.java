package io.aegisops.security;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.security")
public class AiopsSecurityProperties {
  private String internalAgentJwtIssuerUri = "";
  private String internalAgentJwtJwkSetUri = "";
  private String internalAgentJwtAudience = "aegisops-internal-api";
  private String diagnosisGrantSecret = "dev-diagnosis-grant-secret-change-me";
  private String diagnosisGrantAudience = "aegisops-internal-api";
  private List<String> diagnosisGrantIssuers = List.of("aiops-server", "aiops-worker");
  private boolean tenantRequired = true;
  private List<String> allowedOrigins = List.of("http://localhost:5173", "http://127.0.0.1:5173");

  public String getInternalAgentJwtIssuerUri() {
    return internalAgentJwtIssuerUri;
  }

  public void setInternalAgentJwtIssuerUri(String internalAgentJwtIssuerUri) {
    this.internalAgentJwtIssuerUri = internalAgentJwtIssuerUri;
  }

  public String getInternalAgentJwtJwkSetUri() {
    return internalAgentJwtJwkSetUri;
  }

  public void setInternalAgentJwtJwkSetUri(String internalAgentJwtJwkSetUri) {
    this.internalAgentJwtJwkSetUri = internalAgentJwtJwkSetUri;
  }

  public String getInternalAgentJwtAudience() {
    return internalAgentJwtAudience;
  }

  public void setInternalAgentJwtAudience(String internalAgentJwtAudience) {
    this.internalAgentJwtAudience = internalAgentJwtAudience;
  }

  public String getDiagnosisGrantSecret() {
    return diagnosisGrantSecret;
  }

  public void setDiagnosisGrantSecret(String diagnosisGrantSecret) {
    this.diagnosisGrantSecret = diagnosisGrantSecret;
  }

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

  void validateInternalServiceAuthentication() {
    requireText(
        internalAgentJwtIssuerUri, "aiops.security.internal-agent-jwt-issuer-uri is required");
    requireText(
        internalAgentJwtJwkSetUri, "aiops.security.internal-agent-jwt-jwk-set-uri is required");
    requireText(internalAgentJwtAudience, "aiops.security.internal-agent-jwt-audience is required");
    validateDiagnosisGrant();
  }

  void validateDiagnosisGrant() {
    requireText(diagnosisGrantAudience, "aiops.security.diagnosis-grant-audience is required");
    if (diagnosisGrantSecret == null
        || diagnosisGrantSecret.isBlank()
        || diagnosisGrantSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException(
          "aiops.security.diagnosis-grant-secret must contain at least 32 UTF-8 bytes");
    }
    if (diagnosisGrantIssuers.isEmpty()
        || diagnosisGrantIssuers.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new IllegalStateException(
          "aiops.security.diagnosis-grant-issuers must contain non-blank issuers");
    }
  }

  private static void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(message);
    }
  }
}

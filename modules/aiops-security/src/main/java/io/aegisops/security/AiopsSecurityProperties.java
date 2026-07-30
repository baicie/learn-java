package io.aegisops.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.security")
public class AiopsSecurityProperties {
  private String internalAgentToken = "dev-internal-agent-token";
  private boolean internalAgentTokenRequired = true;
  private String internalAgentAuthMode = "static";
  private String internalAgentJwtIssuerUri = "";
  private String internalAgentJwtJwkSetUri = "";
  private String internalAgentJwtAudience = "aegisops-internal-api";
  private String diagnosisGrantSecret = "dev-diagnosis-grant-secret-change-me";
  private String diagnosisGrantAudience = "aegisops-internal-api";
  private List<String> diagnosisGrantIssuers =
      List.of("aiops-server", "aiops-worker", "aiops-java");
  private boolean diagnosisGrantRequired = true;
  private boolean tenantRequired = true;
  private List<String> allowedOrigins = List.of("http://localhost:5173", "http://127.0.0.1:5173");

  public String getInternalAgentToken() {
    return internalAgentToken;
  }

  public String getInternalAgentAuthMode() {
    return internalAgentAuthMode;
  }

  public void setInternalAgentAuthMode(String internalAgentAuthMode) {
    this.internalAgentAuthMode = internalAgentAuthMode;
  }

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

  public boolean internalAgentOAuth2Enabled() {
    return "oauth2"
        .equalsIgnoreCase(internalAgentAuthMode == null ? "" : internalAgentAuthMode.trim());
  }

  public void setInternalAgentToken(String internalAgentToken) {
    this.internalAgentToken = internalAgentToken;
  }

  public boolean isInternalAgentTokenRequired() {
    return internalAgentTokenRequired;
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

  public boolean isDiagnosisGrantRequired() {
    return diagnosisGrantRequired;
  }

  public void setDiagnosisGrantRequired(boolean diagnosisGrantRequired) {
    this.diagnosisGrantRequired = diagnosisGrantRequired;
  }

  public void setInternalAgentTokenRequired(boolean internalAgentTokenRequired) {
    this.internalAgentTokenRequired = internalAgentTokenRequired;
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
}

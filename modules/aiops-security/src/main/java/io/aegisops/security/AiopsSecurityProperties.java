package io.aegisops.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.security")
public class AiopsSecurityProperties {
  private String internalAgentToken = "dev-internal-agent-token";
  private boolean internalAgentTokenRequired = true;
  private boolean tenantRequired = true;
  private List<String> allowedOrigins = List.of("http://localhost:5173", "http://127.0.0.1:5173");

  public String getInternalAgentToken() {
    return internalAgentToken;
  }

  public void setInternalAgentToken(String internalAgentToken) {
    this.internalAgentToken = internalAgentToken;
  }

  public boolean isInternalAgentTokenRequired() {
    return internalAgentTokenRequired;
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

package io.aegisops.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.security")
public class AiopsSecurityProperties {
  private String internalAgentToken = "dev-internal-agent-token";
  private boolean internalAgentTokenRequired = true;
  private boolean tenantRequired = true;

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
}

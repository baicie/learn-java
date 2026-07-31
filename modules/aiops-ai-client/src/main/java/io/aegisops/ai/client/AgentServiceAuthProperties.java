package io.aegisops.ai.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.agent.auth")
public class AgentServiceAuthProperties {
  private String tokenUri = "";
  private String clientId = "";
  private String clientSecret = "";
  private String scope = "agent:diagnose";

  public String getTokenUri() {
    return tokenUri;
  }

  public void setTokenUri(String tokenUri) {
    this.tokenUri = tokenUri;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  void validate() {
    if (tokenUri == null || tokenUri.isBlank()) {
      throw new IllegalStateException("aiops.agent.auth.token-uri is required");
    }
    if (clientId == null || clientId.isBlank()) {
      throw new IllegalStateException("aiops.agent.auth.client-id is required");
    }
    if (clientSecret == null || clientSecret.isBlank()) {
      throw new IllegalStateException("aiops.agent.auth.client-secret is required");
    }
  }
}

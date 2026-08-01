package io.aegisops.ai.client;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.agent.grant")
public class AgentGrantProperties {
  private String issuer = "aiops-server";
  private String audience = "aegisops-internal-api";
  private String secret = "dev-diagnosis-grant-secret-change-me";
  private long ttlSeconds = 300;

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public String getAudience() {
    return audience;
  }

  public void setAudience(String audience) {
    this.audience = audience;
  }

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public long getTtlSeconds() {
    return ttlSeconds;
  }

  public void setTtlSeconds(long ttlSeconds) {
    this.ttlSeconds = ttlSeconds;
  }

  void validate() {
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalStateException("aiops.agent.grant.issuer is required");
    }
    if (audience == null || audience.isBlank()) {
      throw new IllegalStateException("aiops.agent.grant.audience is required");
    }
    if (secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException(
          "aiops.agent.grant.secret must contain at least 32 UTF-8 bytes");
    }
    if (ttlSeconds < 1 || ttlSeconds > 300) {
      throw new IllegalStateException("aiops.agent.grant.ttl-seconds must be between 1 and 300");
    }
  }
}

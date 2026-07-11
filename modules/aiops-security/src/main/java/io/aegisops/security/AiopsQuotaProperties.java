package io.aegisops.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.quota")
public class AiopsQuotaProperties {
  private int publicApiRequestsPerMinute = 600;
  private int internalAgentRequestsPerMinute = 1200;
  private boolean rateLimitEnabled = true;
  private String backend = "redis";

  public int getPublicApiRequestsPerMinute() {
    return publicApiRequestsPerMinute;
  }

  public void setPublicApiRequestsPerMinute(int publicApiRequestsPerMinute) {
    this.publicApiRequestsPerMinute = publicApiRequestsPerMinute;
  }

  public int getInternalAgentRequestsPerMinute() {
    return internalAgentRequestsPerMinute;
  }

  public void setInternalAgentRequestsPerMinute(int internalAgentRequestsPerMinute) {
    this.internalAgentRequestsPerMinute = internalAgentRequestsPerMinute;
  }

  public boolean isRateLimitEnabled() {
    return rateLimitEnabled;
  }

  public void setRateLimitEnabled(boolean rateLimitEnabled) {
    this.rateLimitEnabled = rateLimitEnabled;
  }

  public String getBackend() {
    return backend;
  }

  public void setBackend(String backend) {
    this.backend = backend;
  }
}
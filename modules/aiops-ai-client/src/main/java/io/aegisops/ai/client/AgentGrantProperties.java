package io.aegisops.ai.client;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.agent.grant")
public class AgentGrantProperties {
  private String issuer = "aegisops-app";
  private List<String> audiences = List.of("aiops-agent-api", "aegisops-internal-api");
  private List<String> scopes = List.of("diagnosis:execute", "diagnosis:resume");
  private String keyId = "task-grant-v1";
  private String privateKeyFile = "";
  private long ttlSeconds = 300;

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public List<String> getAudiences() {
    return audiences;
  }

  public void setAudiences(List<String> audiences) {
    this.audiences = audiences == null ? List.of() : List.copyOf(audiences);
  }

  public List<String> getScopes() {
    return scopes;
  }

  public void setScopes(List<String> scopes) {
    this.scopes = scopes == null ? List.of() : List.copyOf(scopes);
  }

  public String getKeyId() {
    return keyId;
  }

  public void setKeyId(String keyId) {
    this.keyId = keyId;
  }

  public String getPrivateKeyFile() {
    return privateKeyFile;
  }

  public void setPrivateKeyFile(String privateKeyFile) {
    this.privateKeyFile = privateKeyFile;
  }

  public long getTtlSeconds() {
    return ttlSeconds;
  }

  public void setTtlSeconds(long ttlSeconds) {
    this.ttlSeconds = ttlSeconds;
  }

  void validateMetadata() {
    requireText(issuer, "aiops.agent.grant.issuer is required");
    requireText(keyId, "aiops.agent.grant.key-id is required");
    requireValues(audiences, "aiops.agent.grant.audiences must contain non-blank audiences");
    requireValues(scopes, "aiops.agent.grant.scopes must contain non-blank scopes");
    if (!scopes.contains("diagnosis:execute")) {
      throw new IllegalStateException("aiops.agent.grant.scopes must contain diagnosis:execute");
    }
    if (ttlSeconds < 1 || ttlSeconds > 300) {
      throw new IllegalStateException("aiops.agent.grant.ttl-seconds must be between 1 and 300");
    }
  }

  void validate() {
    validateMetadata();
    requireText(privateKeyFile, "aiops.agent.grant.private-key-file is required");
  }

  private static void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(message);
    }
  }

  private static void requireValues(List<String> values, String message) {
    if (values.isEmpty() || values.stream().anyMatch(value -> value == null || value.isBlank())) {
      throw new IllegalStateException(message);
    }
  }
}

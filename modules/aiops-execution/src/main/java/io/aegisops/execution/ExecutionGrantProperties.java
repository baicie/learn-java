package io.aegisops.execution;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.execution.grant")
public class ExecutionGrantProperties {
  private String issuer = "aegisops-app";
  private String audience = "aiops-runner";
  private String scope = "runbook:execute";
  private String keyId = "task-grant-v1";
  private String privateKeyFile = "";
  private int queueWaitSeconds = 1800;

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

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
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

  public int getQueueWaitSeconds() {
    return queueWaitSeconds;
  }

  public void setQueueWaitSeconds(int queueWaitSeconds) {
    this.queueWaitSeconds = queueWaitSeconds;
  }

  void validateMetadata() {
    requireText(issuer, "aiops.execution.grant.issuer is required");
    requireText(audience, "aiops.execution.grant.audience is required");
    requireText(scope, "aiops.execution.grant.scope is required");
    if (!"aegisops-app".equals(issuer)) {
      throw new IllegalStateException("Execution grant issuer must be aegisops-app");
    }
    if (!"aiops-runner".equals(audience)) {
      throw new IllegalStateException("Execution grant audience must be aiops-runner");
    }
    if (!"runbook:execute".equals(scope)) {
      throw new IllegalStateException("Execution grant scope must be runbook:execute");
    }
    requireText(keyId, "aiops.execution.grant.key-id is required");
    if (queueWaitSeconds < 0 || queueWaitSeconds > 86_400) {
      throw new IllegalStateException(
          "aiops.execution.grant.queue-wait-seconds must be between 0 and 86400");
    }
  }

  void validateSigningKey() {
    requireText(privateKeyFile, "aiops.execution.grant.private-key-file is required");
  }

  private static void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(message);
    }
  }
}

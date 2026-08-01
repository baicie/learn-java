package io.aegisops.runner;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aiops.runner.execution-grant")
public class RunnerExecutionGrantProperties {
  private String issuer = "aegisops-app";
  private String audience = "aiops-runner";
  private String scope = "runbook:execute";
  private String currentKeyId = "task-grant-v1";
  private String currentPublicKeyFile = "";
  private String previousKeyId = "";
  private String previousPublicKeyFile = "";

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

  public String getCurrentKeyId() {
    return currentKeyId;
  }

  public void setCurrentKeyId(String currentKeyId) {
    this.currentKeyId = currentKeyId;
  }

  public String getCurrentPublicKeyFile() {
    return currentPublicKeyFile;
  }

  public void setCurrentPublicKeyFile(String currentPublicKeyFile) {
    this.currentPublicKeyFile = currentPublicKeyFile;
  }

  public String getPreviousKeyId() {
    return previousKeyId;
  }

  public void setPreviousKeyId(String previousKeyId) {
    this.previousKeyId = previousKeyId;
  }

  public String getPreviousPublicKeyFile() {
    return previousPublicKeyFile;
  }

  public void setPreviousPublicKeyFile(String previousPublicKeyFile) {
    this.previousPublicKeyFile = previousPublicKeyFile;
  }

  void validateMetadata() {
    if (!"aegisops-app".equals(issuer)) {
      throw new IllegalStateException("Runner execution grant issuer must be aegisops-app");
    }
    if (!"aiops-runner".equals(audience)) {
      throw new IllegalStateException("Runner execution grant audience must be aiops-runner");
    }
    if (!"runbook:execute".equals(scope)) {
      throw new IllegalStateException("Runner execution grant scope must be runbook:execute");
    }
    requireText(currentKeyId, "Runner execution grant current key id is required");
    boolean previousIdConfigured = !isBlank(previousKeyId);
    boolean previousFileConfigured = !isBlank(previousPublicKeyFile);
    if (previousIdConfigured != previousFileConfigured) {
      throw new IllegalStateException(
          "Previous execution grant key id and public key file must be configured together");
    }
  }

  void validateKeyFiles() {
    requireText(currentPublicKeyFile, "Runner execution grant current public key file is required");
  }

  private static void requireText(String value, String message) {
    if (isBlank(value)) {
      throw new IllegalStateException(message);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}

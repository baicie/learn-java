package io.aegisops.kubernetes.domain.model;

public record KubernetesConfig(String endpoint, String apiToken, int timeoutSeconds) {
  public KubernetesConfig {
    timeoutSeconds = timeoutSeconds <= 0 ? 10 : timeoutSeconds;
  }
}

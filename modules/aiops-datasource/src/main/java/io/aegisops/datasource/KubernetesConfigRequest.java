package io.aegisops.datasource;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record KubernetesConfigRequest(
    @NotBlank String endpoint, @NotBlank String apiToken, @Min(1) int timeoutSeconds) {
  public KubernetesConfigRequest {
    timeoutSeconds = timeoutSeconds <= 0 ? 10 : timeoutSeconds;
  }
}

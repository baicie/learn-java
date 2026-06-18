package io.aegisops.execution.dto;

public record ExecutionCreateRequest(Boolean dryRun, String requestedBy, Integer maxAttempts) {
  public boolean dryRunEnabled() {
    return dryRun == null || dryRun;
  }

  public int normalizedMaxAttempts() {
    if (maxAttempts == null) {
      return 1;
    }
    return Math.max(1, Math.min(maxAttempts, 5));
  }
}

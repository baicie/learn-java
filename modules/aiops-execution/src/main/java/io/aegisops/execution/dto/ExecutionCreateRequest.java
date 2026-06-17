package io.aegisops.execution.dto;

public record ExecutionCreateRequest(Boolean dryRun, String requestedBy) {
  public boolean dryRunEnabled() {
    return dryRun == null || dryRun;
  }
}

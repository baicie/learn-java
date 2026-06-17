package io.aegisops.runbook.dto;

public record RecommendPlanRequest(Boolean force) {
  public boolean forceEnabled() {
    return Boolean.TRUE.equals(force);
  }
}

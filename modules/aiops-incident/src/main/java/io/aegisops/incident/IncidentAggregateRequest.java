package io.aegisops.incident;

public record IncidentAggregateRequest(Integer windowMinutes, Integer limit) {
  public int normalizedWindowMinutes() {
    if (windowMinutes == null || windowMinutes <= 0) {
      return 60 * 24;
    }

    return Math.min(windowMinutes, 60 * 24 * 7);
  }

  public int normalizedLimit() {
    if (limit == null || limit <= 0) {
      return 500;
    }

    return Math.min(limit, 5000);
  }
}

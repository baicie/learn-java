package io.aegisops.evidence.dto;

import java.util.List;

public record MetricEvidence(
    boolean available, String reason, List<MetricSeriesSummary> series) {
  public static MetricEvidence unavailable(String reason) {
    return new MetricEvidence(false, reason, List.of());
  }
}

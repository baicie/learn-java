package io.aegisops.evidence.dto;

import java.util.List;
import java.util.Map;

public record MultiSourceEvidence(
    boolean available,
    String reason,
    List<SourceEvidenceItem> items,
    long affectedSessions,
    long affectedPages,
    Map<String, WebVitalSummary> webVitals) {
  public static MultiSourceEvidence unavailable(String reason) {
    return new MultiSourceEvidence(false, reason, List.of(), 0, 0, Map.of());
  }
}

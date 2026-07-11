package io.aegisops.evidence.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record EvidenceQueryRequest(
    String contractVersion,
    String tenantId,
    String incidentId,
    String traceId,
    String primaryAssetId,
    OffsetDateTime startedAt,
    OffsetDateTime lastSeenAt,
    List<String> alertFingerprints,
    List<String> alertTitles,
    List<String> serviceNames) {
  public List<String> normalizedAlertFingerprints() {
    return normalize(alertFingerprints);
  }

  public List<String> normalizedAlertTitles() {
    return normalize(alertTitles);
  }

  public List<String> normalizedServiceNames() {
    return normalize(serviceNames);
  }

  private static List<String> normalize(List<String> values) {
    if (values == null) {
      return List.of();
    }

    return values.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(text -> text.trim())
        .distinct()
        .toList();
  }
}

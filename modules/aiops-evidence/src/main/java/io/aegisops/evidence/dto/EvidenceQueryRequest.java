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
    List<String> alertTitles) {
  public List<String> normalizedAlertFingerprints() {
    return alertFingerprints == null ? List.of() : alertFingerprints;
  }

  public List<String> normalizedAlertTitles() {
    return alertTitles == null ? List.of() : alertTitles;
  }
}

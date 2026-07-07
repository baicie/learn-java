package io.aegisops.evidence;

import java.time.OffsetDateTime;

public record EvidenceCollectRequest(
    Integer lookbackMinutes, OffsetDateTime timeFrom, OffsetDateTime timeTo, String collectorKey) {
  public int normalizedLookbackMinutes() {
    if (lookbackMinutes == null || lookbackMinutes <= 0) {
      return 30;
    }
    return Math.min(lookbackMinutes, 240);
  }
}

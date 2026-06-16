package io.aegisops.evidence.dto;

import java.util.List;

public record ChangeEvidence(boolean available, String reason, List<ChangeEvidenceEvent> events) {
  public static ChangeEvidence unavailable(String reason) {
    return new ChangeEvidence(false, reason, List.of());
  }
}

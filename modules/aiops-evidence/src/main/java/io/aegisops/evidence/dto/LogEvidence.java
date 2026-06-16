package io.aegisops.evidence.dto;

import java.util.List;

public record LogEvidence(boolean available, String reason, List<LogPattern> patterns) {
  public static LogEvidence unavailable(String reason) {
    return new LogEvidence(false, reason, List.of());
  }
}

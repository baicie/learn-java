package io.aegisops.ai.client.workrecord;

import java.util.List;
import java.util.Map;

public record WorkRecordGenerationResponse(
    String contractVersion,
    String provider,
    String model,
    String promptVersion,
    String markdown,
    List<String> warnings,
    Map<String, Object> raw) {
  public WorkRecordGenerationResponse {
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
    raw = raw == null ? Map.of() : Map.copyOf(raw);
  }
}

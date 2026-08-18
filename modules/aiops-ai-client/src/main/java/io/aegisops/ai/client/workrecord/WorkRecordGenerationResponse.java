package io.aegisops.ai.client.workrecord;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record WorkRecordGenerationResponse(
    String contractVersion,
    String provider,
    String model,
    String promptVersion,
    String markdown,
    List<String> warnings,
    String providerRunId,
    String providerWorkflowId,
    String providerWorkflowVersion,
    Long providerDurationMs,
    Long providerTotalTokens,
    String fallbackReason,
    Map<String, Object> raw) {
  public WorkRecordGenerationResponse {
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
    if (raw == null || raw.isEmpty()) {
      raw = Map.of();
    } else {
      Map<String, Object> sanitized = new LinkedHashMap<>(raw.size());
      for (var e : raw.entrySet()) {
        if (e.getKey() != null && e.getValue() != null) {
          sanitized.put(e.getKey(), e.getValue());
        }
      }
      raw = sanitized.isEmpty() ? Map.of() : Map.copyOf(sanitized);
    }
  }
}

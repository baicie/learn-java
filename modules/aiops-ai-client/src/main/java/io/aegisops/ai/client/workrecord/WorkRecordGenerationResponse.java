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
    String providerRunId,
    String providerWorkflowId,
    String providerWorkflowVersion,
    Long providerDurationMs,
    Long providerTotalTokens,
    String fallbackReason,
    Map<String, Object> raw) {
  public WorkRecordGenerationResponse {
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
    raw = raw == null ? Map.of() : Map.copyOf(raw);
  }
}

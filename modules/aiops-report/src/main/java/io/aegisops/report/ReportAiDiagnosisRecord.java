package io.aegisops.report;

import java.time.OffsetDateTime;

public record ReportAiDiagnosisRecord(
    String id,
    String provider,
    String model,
    String agentName,
    String summary,
    String rootCause,
    String impact,
    String nextStepsJson,
    String runbookSuggestionsJson,
    String risksJson,
    String responseRawJson,
    OffsetDateTime createdAt) {}

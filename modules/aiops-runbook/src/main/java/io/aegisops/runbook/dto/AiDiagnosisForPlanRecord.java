package io.aegisops.runbook.dto;

import java.time.OffsetDateTime;

public record AiDiagnosisForPlanRecord(
    String id,
    String summary,
    String rootCause,
    String impact,
    String nextStepsJson,
    String runbookSuggestionsJson,
    String risksJson,
    OffsetDateTime createdAt) {}

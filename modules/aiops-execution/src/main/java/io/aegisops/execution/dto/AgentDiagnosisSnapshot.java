package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record AgentDiagnosisSnapshot(
    String id,
    String incidentId,
    String summary,
    String rootCause,
    String recommendation,
    OffsetDateTime createdAt) {}

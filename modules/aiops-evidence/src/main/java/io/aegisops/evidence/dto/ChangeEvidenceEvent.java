package io.aegisops.evidence.dto;

import java.time.OffsetDateTime;

public record ChangeEvidenceEvent(
    String id,
    String changeType,
    String title,
    String description,
    String source,
    String operator,
    String riskLevel,
    OffsetDateTime occurredAt) {}

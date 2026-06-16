package io.aegisops.ai.client.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AgentEvalResultRecord(
    String id,
    String evaluatorName,
    String checkName,
    boolean passed,
    BigDecimal score,
    String reason,
    String detailsJson,
    OffsetDateTime createdAt) {}

package io.aegisops.ai.client.dto;

import java.math.BigDecimal;

public record AgentEvalResultCommand(
    String id,
    String runId,
    String evaluatorName,
    String checkName,
    boolean passed,
    BigDecimal score,
    String reason,
    String detailsJson) {}

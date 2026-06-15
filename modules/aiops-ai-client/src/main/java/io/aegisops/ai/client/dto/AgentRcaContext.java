package io.aegisops.ai.client.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AgentRcaContext(
    String id,
    String suspectedRootCause,
    BigDecimal confidence,
    String summary,
    String evidenceJson,
    String modelVersion,
    OffsetDateTime createdAt) {}

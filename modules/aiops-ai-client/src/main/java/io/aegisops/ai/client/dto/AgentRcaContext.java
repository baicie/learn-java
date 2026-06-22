package io.aegisops.ai.client.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AgentRcaContext(
    String id,
    String suspectedRootCause,
    BigDecimal confidence,
    String summary,
    String evidenceJson,
    List<String> matchedRules,
    List<String> evidenceRefs,
    String modelVersion,
    OffsetDateTime createdAt) {}

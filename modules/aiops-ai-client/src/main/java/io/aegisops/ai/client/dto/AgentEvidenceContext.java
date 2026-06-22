package io.aegisops.ai.client.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AgentEvidenceContext(
    String id,
    String evidenceKey,
    String source,
    String evidenceType,
    String title,
    String summary,
    OffsetDateTime timeRangeStart,
    OffsetDateTime timeRangeEnd,
    BigDecimal confidence,
    String payloadJson) {}

package io.aegisops.runbook.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RcaForPlanRecord(
    String id,
    String suspectedRootCause,
    BigDecimal confidence,
    String summary,
    String evidenceJson,
    OffsetDateTime createdAt) {}

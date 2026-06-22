package io.aegisops.rca;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RcaDiagnosisEvidenceRecord(
    String id,
    String incidentId,
    String evidenceKey,
    String source,
    String evidenceType,
    String title,
    String summary,
    OffsetDateTime timeRangeStart,
    OffsetDateTime timeRangeEnd,
    BigDecimal confidence,
    String payloadJson) {}

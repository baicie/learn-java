package io.aegisops.evidence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public record DiagnosisEvidenceDraft(
    String evidenceKey,
    String source,
    String evidenceType,
    String title,
    String summary,
    OffsetDateTime timeRangeStart,
    OffsetDateTime timeRangeEnd,
    BigDecimal confidence,
    Map<String, Object> payload) {}

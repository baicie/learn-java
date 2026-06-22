package io.aegisops.evidence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DiagnosisEvidenceRecord(
    String id,
    String tenantId,
    String incidentId,
    String evidenceKey,
    String source,
    String evidenceType,
    String title,
    String summary,
    OffsetDateTime timeRangeStart,
    OffsetDateTime timeRangeEnd,
    BigDecimal confidence,
    String payloadJson,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

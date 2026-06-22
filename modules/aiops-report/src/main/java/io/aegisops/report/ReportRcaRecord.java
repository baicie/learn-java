package io.aegisops.report;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ReportRcaRecord(
    String id,
    String suspectedRootCause,
    BigDecimal confidence,
    String summary,
    String evidenceJson,
    String modelVersion,
    OffsetDateTime createdAt) {}

package io.aegisops.report;

import java.time.OffsetDateTime;

public record ReportTimelineRecord(
    String id,
    OffsetDateTime eventTime,
    String eventType,
    String title,
    String description,
    String source,
    String payloadJson) {}

package io.aegisops.alert;

import java.time.OffsetDateTime;

public record AlertEventRecord(
        String id,
        String tenantId,
        String source,
        String severity,
        String title,
        String status,
        OffsetDateTime startsAt,
        OffsetDateTime createdAt
) {}

package io.aegisops.incident;

import java.time.OffsetDateTime;

public record IncidentRecord(
        String id,
        String tenantId,
        String title,
        String severity,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime createdAt
) {}

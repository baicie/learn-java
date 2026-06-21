package io.aegisops.incident;

import java.time.OffsetDateTime;

public record IncidentUpdateCommand(
    String tenantId,
    String incidentId,
    String title,
    String summary,
    String severity,
    int alertCount,
    OffsetDateTime lastSeenAt) {}

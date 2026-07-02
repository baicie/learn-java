package io.aegisops.evidence;

import java.time.OffsetDateTime;

public record EvidenceCollectionTaskRecord(
    String id,
    String tenantId,
    String incidentId,
    String collectorKey,
    String status,
    String requestJson,
    String resultJson,
    String errorMessage,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime createdAt) {}

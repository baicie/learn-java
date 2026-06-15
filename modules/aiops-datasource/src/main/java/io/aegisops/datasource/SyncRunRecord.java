package io.aegisops.datasource;

import java.time.OffsetDateTime;

public record SyncRunRecord(
    String id,
    String datasourceId,
    String syncType,
    String status,
    String message,
    String statsJson,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt) {}

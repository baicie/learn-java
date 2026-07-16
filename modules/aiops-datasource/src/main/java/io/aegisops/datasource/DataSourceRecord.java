package io.aegisops.datasource;

import java.time.OffsetDateTime;

public record DataSourceRecord(
    String id,
    String tenantId,
    String type,
    String name,
    String endpoint,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime lastSyncAt) {}

package io.aegisops.datasource;

import java.time.OffsetDateTime;

record DataSourceEntity(
        String id,
        String tenantId,
        String type,
        String name,
        String status,
        String configJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime lastSyncAt
) {}

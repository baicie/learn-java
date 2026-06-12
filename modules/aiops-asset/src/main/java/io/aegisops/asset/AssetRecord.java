package io.aegisops.asset;

import java.time.OffsetDateTime;

public record AssetRecord(
        String id,
        String tenantId,
        String assetType,
        String name,
        String displayName,
        String source,
        String status,
        OffsetDateTime createdAt
) {}

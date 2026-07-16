package io.aegisops.asset.api.dto;

import java.time.OffsetDateTime;

public record AssetSourceResponse(
    String id,
    String sourceType,
    String sourceInstanceId,
    String datasourceId,
    String externalId,
    String ingestionChannel,
    String syncStatus,
    OffsetDateTime firstSeenAt,
    OffsetDateTime lastSeenAt) {}

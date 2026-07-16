package io.aegisops.asset.api.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record AssetResponse(
    String id,
    String assetType,
    String name,
    String displayName,
    String description,
    String environment,
    String ip,
    String site,
    String ownerTeam,
    String criticality,
    Map<String, Object> tags,
    String status,
    int sourceCount,
    OffsetDateTime lastSeenAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    long version) {}

package io.aegisops.asset.domain.model;

import java.time.OffsetDateTime;

public record Asset(
    String id,
    String tenantId,
    String assetType,
    String name,
    String displayName,
    String source,
    String status,
    OffsetDateTime createdAt) {}

package io.aegisops.asset.api.dto;

public record AssetUpsertResult(
    String assetId, String sourceLinkId, String action, boolean hasWeakIdentityConflict) {}

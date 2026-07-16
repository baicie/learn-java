package io.aegisops.asset.api.dto;

public record AssetSummaryResponse(
    long totalAssets, long activeAssets, long multiSourceAssets, long pendingConflicts) {}

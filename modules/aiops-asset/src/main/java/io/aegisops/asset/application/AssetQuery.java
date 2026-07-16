package io.aegisops.asset.application;

public record AssetQuery(
    int page, int pageSize, String assetType, String sourceType, String keyword, String status) {}

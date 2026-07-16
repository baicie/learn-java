package io.aegisops.asset.domain.model;

public record AssetIdentityInput(
    String identityType, String scopeKey, String identityValue, boolean verified) {}

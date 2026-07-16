package io.aegisops.asset.api.dto;

public record AssetIdentityResponse(
    String id,
    String sourceLinkId,
    String identityType,
    String scopeKey,
    String identityValue,
    String normalizedValue,
    String strength,
    boolean verified) {}

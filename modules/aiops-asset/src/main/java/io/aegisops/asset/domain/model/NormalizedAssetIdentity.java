package io.aegisops.asset.domain.model;

public record NormalizedAssetIdentity(
    String identityType,
    String scopeKey,
    String identityValue,
    String normalizedValue,
    Strength strength,
    boolean verified) {

  public enum Strength {
    STRONG,
    WEAK
  }
}

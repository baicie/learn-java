package io.aegisops.asset.domain.rule;

public class AssetIdentityConflictException extends IllegalStateException {
  public AssetIdentityConflictException(String message) {
    super(message);
  }
}

package io.aegisops.asset.domain.rule;

import java.util.Optional;
import java.util.Set;

public class AssetIdentityResolver {

  public Resolution resolve(
      Optional<String> sourceLinkAssetId,
      Set<String> strongIdentityAssetIds,
      boolean hasWeakIdentityConflict) {
    Optional<String> sourceAsset = sourceLinkAssetId == null ? Optional.empty() : sourceLinkAssetId;
    Set<String> strongAssets = strongIdentityAssetIds == null ? Set.of() : Set.copyOf(strongIdentityAssetIds);

    if (strongAssets.size() > 1) {
      throw new AssetIdentityConflictException("strong identities resolve to multiple assets");
    }
    if (sourceAsset.isPresent()
        && !strongAssets.isEmpty()
        && !strongAssets.contains(sourceAsset.orElseThrow())) {
      throw new AssetIdentityConflictException(
          "source link and strong identities resolve to different assets");
    }
    if (sourceAsset.isPresent()) {
      return new Resolution(sourceAsset, Action.UPDATE, hasWeakIdentityConflict);
    }
    if (!strongAssets.isEmpty()) {
      return new Resolution(
          Optional.of(strongAssets.iterator().next()), Action.LINK, hasWeakIdentityConflict);
    }
    return new Resolution(Optional.empty(), Action.CREATE, hasWeakIdentityConflict);
  }

  public enum Action {
    CREATE,
    UPDATE,
    LINK
  }

  public record Resolution(
      Optional<String> assetId, Action action, boolean hasWeakIdentityConflict) {}
}

package io.aegisops.asset.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AssetIdentityResolverTest {

  private final AssetIdentityResolver resolver = new AssetIdentityResolver();

  @Test
  void exactSourceLinkMustReuseCanonicalAsset() {
    var resolution = resolver.resolve(Optional.of("asset-source"), Set.of("asset-source"), false);

    assertThat(resolution.assetId()).contains("asset-source");
    assertThat(resolution.action()).isEqualTo(AssetIdentityResolver.Action.UPDATE);
  }

  @Test
  void sameStrongIdentityAcrossSourcesMustLinkOneAsset() {
    var resolution = resolver.resolve(Optional.empty(), Set.of("asset-strong"), false);

    assertThat(resolution.assetId()).contains("asset-strong");
    assertThat(resolution.action()).isEqualTo(AssetIdentityResolver.Action.LINK);
  }

  @Test
  void ipOnlyMatchMustCreateNewAssetAndReportConflict() {
    var resolution = resolver.resolve(Optional.empty(), Set.of(), true);

    assertThat(resolution.assetId()).isEmpty();
    assertThat(resolution.action()).isEqualTo(AssetIdentityResolver.Action.CREATE);
    assertThat(resolution.hasWeakIdentityConflict()).isTrue();
  }

  @Test
  void conflictingStrongIdentitiesMustBeRejected() {
    assertThatThrownBy(
            () -> resolver.resolve(Optional.empty(), Set.of("asset-a", "asset-b"), false))
        .isInstanceOf(AssetIdentityConflictException.class)
        .hasMessageContaining("multiple assets");
  }
}

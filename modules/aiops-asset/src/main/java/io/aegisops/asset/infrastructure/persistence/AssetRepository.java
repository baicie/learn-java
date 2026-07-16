package io.aegisops.asset.infrastructure.persistence;

import io.aegisops.asset.domain.model.Asset;
import io.aegisops.asset.api.dto.AssetUpsertCommand;
import io.aegisops.asset.domain.model.NormalizedAssetIdentity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AssetRepository {
  List<Asset> listRecent(String tenantId, int limit);

  Optional<String> findAssetIdBySourceLink(
      String tenantId, String sourceType, String sourceInstanceId, String externalId);

  Set<String> findAssetIdsByStrongIdentities(
      String tenantId, List<NormalizedAssetIdentity> identities);

  boolean hasWeakIdentityConflict(
      String tenantId, List<NormalizedAssetIdentity> identities, String excludedAssetId);

  String createAsset(AssetUpsertCommand command, OffsetDateTime now);

  void updateAsset(String assetId, AssetUpsertCommand command, OffsetDateTime now);

  String upsertSourceLink(String assetId, AssetUpsertCommand command, OffsetDateTime now);

  void replaceSourceIdentities(
      String assetId,
      String sourceLinkId,
      String tenantId,
      List<NormalizedAssetIdentity> identities,
      OffsetDateTime now);
}

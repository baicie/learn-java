package io.aegisops.asset.infrastructure.persistence;

import io.aegisops.asset.api.dto.AssetIdentityResponse;
import io.aegisops.asset.api.dto.AssetPageResponse;
import io.aegisops.asset.api.dto.AssetRelationResponse;
import io.aegisops.asset.api.dto.AssetResponse;
import io.aegisops.asset.api.dto.AssetSourceResponse;
import io.aegisops.asset.api.dto.AssetUpsertCommand;
import io.aegisops.asset.api.dto.CreateAssetRelationRequest;
import io.aegisops.asset.api.dto.UpdateAssetRequest;
import io.aegisops.asset.application.AssetQuery;
import io.aegisops.asset.domain.model.Asset;
import io.aegisops.asset.domain.model.NormalizedAssetIdentity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AssetRepository {
  List<Asset> listRecent(String tenantId, int limit);

  AssetPageResponse page(String tenantId, AssetQuery query);

  Optional<AssetResponse> findById(String tenantId, String assetId);

  List<AssetSourceResponse> listSources(String tenantId, String assetId);

  List<AssetIdentityResponse> listIdentities(String tenantId, String assetId);

  List<AssetRelationResponse> listRelations(String tenantId, String assetId);

  boolean updateCanonical(
      String tenantId, String assetId, UpdateAssetRequest request, OffsetDateTime now);

  boolean archive(String tenantId, String assetId, long version, OffsetDateTime now);

  String createRelation(
      String tenantId,
      String assetId,
      CreateAssetRelationRequest request,
      OffsetDateTime now);

  boolean deleteRelation(String tenantId, String assetId, String relationId);

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

package io.aegisops.asset.application;

import io.aegisops.asset.api.dto.AssetIdentityResponse;
import io.aegisops.asset.api.dto.AssetPageResponse;
import io.aegisops.asset.api.dto.AssetRelationResponse;
import io.aegisops.asset.api.dto.AssetResponse;
import io.aegisops.asset.api.dto.AssetSourceResponse;
import io.aegisops.asset.api.dto.AssetSummaryResponse;
import io.aegisops.asset.application.port.AssetStore;
import io.aegisops.asset.domain.model.Asset;
import io.aegisops.common.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AssetQueryService {
  private static final int RECENT_ASSET_LIMIT = 100;

  private final AssetStore repository;

  public AssetQueryService(AssetStore repository) {
    this.repository = repository;
  }

  public List<Asset> listRecent(String tenantId) {
    return repository.listRecent(tenantId, RECENT_ASSET_LIMIT);
  }

  public AssetPageResponse page(String tenantId, AssetQuery query) {
    return repository.page(tenantId, query);
  }

  public AssetSummaryResponse summary(String tenantId) {
    return repository.summary(tenantId);
  }

  public AssetResponse get(String tenantId, String assetId) {
    return repository
        .findById(tenantId, assetId)
        .orElseThrow(() -> new ResourceNotFoundException("资源不存在: " + assetId));
  }

  public List<AssetSourceResponse> sources(String tenantId, String assetId) {
    get(tenantId, assetId);
    return repository.listSources(tenantId, assetId);
  }

  public List<AssetIdentityResponse> identities(String tenantId, String assetId) {
    get(tenantId, assetId);
    return repository.listIdentities(tenantId, assetId);
  }

  public List<AssetRelationResponse> relations(String tenantId, String assetId) {
    get(tenantId, assetId);
    return repository.listRelations(tenantId, assetId);
  }
}

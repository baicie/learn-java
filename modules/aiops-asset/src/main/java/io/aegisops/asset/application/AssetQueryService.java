package io.aegisops.asset.application;

import io.aegisops.asset.domain.model.Asset;
import io.aegisops.asset.infrastructure.persistence.AssetRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AssetQueryService {
  private static final int RECENT_ASSET_LIMIT = 100;

  private final AssetRepository repository;

  public AssetQueryService(AssetRepository repository) {
    this.repository = repository;
  }

  public List<Asset> listRecent(String tenantId) {
    return repository.listRecent(tenantId, RECENT_ASSET_LIMIT);
  }
}

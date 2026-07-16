package io.aegisops.asset.infrastructure.persistence;

import io.aegisops.asset.domain.model.Asset;
import java.util.List;

public interface AssetRepository {
  List<Asset> listRecent(String tenantId, int limit);
}

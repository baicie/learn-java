package io.aegisops.asset.infrastructure.persistence;

import static io.aegisops.persistence.jooq.public_.tables.Asset.ASSET;

import io.aegisops.asset.domain.model.Asset;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqAssetRepository implements AssetRepository {
  private final DSLContext dsl;

  public JooqAssetRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public List<Asset> listRecent(String tenantId, int limit) {
    return dsl.selectFrom(ASSET)
        .where(ASSET.TENANT_ID.eq(tenantId))
        .and(ASSET.DELETED_AT.isNull())
        .orderBy(ASSET.CREATED_AT.desc())
        .limit(limit)
        .fetch(
            row ->
                new Asset(
                    row.getId(),
                    row.getTenantId(),
                    row.getAssetType(),
                    row.getName(),
                    row.getDisplayName(),
                    row.getSource(),
                    row.getStatus(),
                    row.getCreatedAt()));
  }
}

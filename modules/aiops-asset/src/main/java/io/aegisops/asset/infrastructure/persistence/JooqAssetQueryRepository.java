package io.aegisops.asset.infrastructure.persistence;

import static io.aegisops.persistence.jooq.public_.tables.Asset.ASSET;
import static io.aegisops.persistence.jooq.public_.tables.AssetIdentity.ASSET_IDENTITY;
import static io.aegisops.persistence.jooq.public_.tables.AssetRelation.ASSET_RELATION;
import static io.aegisops.persistence.jooq.public_.tables.AssetSourceLink.ASSET_SOURCE_LINK;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.asset.api.dto.AssetIdentityResponse;
import io.aegisops.asset.api.dto.AssetPageResponse;
import io.aegisops.asset.api.dto.AssetRelationResponse;
import io.aegisops.asset.api.dto.AssetResponse;
import io.aegisops.asset.api.dto.AssetSourceResponse;
import io.aegisops.asset.application.AssetQuery;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
class JooqAssetQueryRepository {
  private final DSLContext dsl;
  private final ObjectMapper objectMapper;

  JooqAssetQueryRepository(DSLContext dsl, ObjectMapper objectMapper) {
    this.dsl = dsl;
    this.objectMapper = objectMapper;
  }

  AssetPageResponse page(String tenantId, AssetQuery query) {
    Condition condition = activeAssetCondition(tenantId, query);
    long total = dsl.fetchCount(dsl.selectFrom(ASSET).where(condition));
    int offset = (query.page() - 1) * query.pageSize();
    List<AssetResponse> items =
        dsl.selectFrom(ASSET)
            .where(condition)
            .orderBy(ASSET.UPDATED_AT.desc(), ASSET.ID.asc())
            .offset(offset)
            .limit(query.pageSize())
            .fetch(this::toResponse);
    return new AssetPageResponse(total, query.page(), query.pageSize(), items);
  }

  Optional<AssetResponse> findById(String tenantId, String assetId) {
    return dsl.selectFrom(ASSET)
        .where(ASSET.TENANT_ID.eq(tenantId))
        .and(ASSET.ID.eq(assetId))
        .and(ASSET.DELETED_AT.isNull())
        .fetchOptional(this::toResponse);
  }

  List<AssetSourceResponse> listSources(String tenantId, String assetId) {
    return dsl.selectFrom(ASSET_SOURCE_LINK)
        .where(ASSET_SOURCE_LINK.TENANT_ID.eq(tenantId))
        .and(ASSET_SOURCE_LINK.ASSET_ID.eq(assetId))
        .orderBy(ASSET_SOURCE_LINK.LAST_SEEN_AT.desc())
        .fetch(
            row ->
                new AssetSourceResponse(
                    row.getId(),
                    row.getSourceType(),
                    row.getSourceInstanceId(),
                    row.getDatasourceId(),
                    row.getExternalId(),
                    row.getIngestionChannel(),
                    row.getSyncStatus(),
                    row.getFirstSeenAt(),
                    row.getLastSeenAt()));
  }

  List<AssetIdentityResponse> listIdentities(String tenantId, String assetId) {
    return dsl.selectFrom(ASSET_IDENTITY)
        .where(ASSET_IDENTITY.TENANT_ID.eq(tenantId))
        .and(ASSET_IDENTITY.ASSET_ID.eq(assetId))
        .orderBy(ASSET_IDENTITY.STRENGTH.asc(), ASSET_IDENTITY.IDENTITY_TYPE.asc())
        .fetch(
            row ->
                new AssetIdentityResponse(
                    row.getId(),
                    row.getSourceLinkId(),
                    row.getIdentityType(),
                    row.getScopeKey(),
                    row.getIdentityValue(),
                    row.getNormalizedValue(),
                    row.getStrength(),
                    Boolean.TRUE.equals(row.getVerified())));
  }

  List<AssetRelationResponse> listRelations(String tenantId, String assetId) {
    return dsl.selectFrom(ASSET_RELATION)
        .where(ASSET_RELATION.TENANT_ID.eq(tenantId))
        .and(ASSET_RELATION.FROM_ASSET_ID.eq(assetId).or(ASSET_RELATION.TO_ASSET_ID.eq(assetId)))
        .orderBy(ASSET_RELATION.CREATED_AT.desc())
        .fetch(
            row ->
                new AssetRelationResponse(
                    row.getId(),
                    row.getFromAssetId(),
                    row.getToAssetId(),
                    row.getRelationType(),
                    row.getConfidence(),
                    row.getSource(),
                    row.getCreatedAt()));
  }

  private Condition activeAssetCondition(String tenantId, AssetQuery query) {
    Condition condition = ASSET.TENANT_ID.eq(tenantId).and(ASSET.DELETED_AT.isNull());
    if (hasText(query.assetType())) {
      condition = condition.and(ASSET.ASSET_TYPE.eq(normalized(query.assetType())));
    }
    if (hasText(query.status())) {
      condition = condition.and(ASSET.STATUS.eq(normalized(query.status())));
    }
    if (hasText(query.keyword())) {
      String pattern = "%" + query.keyword().trim().toLowerCase(Locale.ROOT) + "%";
      condition =
          condition.and(
              DSL.lower(ASSET.NAME)
                  .like(pattern)
                  .or(DSL.lower(ASSET.DISPLAY_NAME).like(pattern))
                  .or(ASSET.IP.like(pattern)));
    }
    if (hasText(query.sourceType())) {
      condition =
          condition.and(
              DSL.exists(
                  DSL.selectOne()
                      .from(ASSET_SOURCE_LINK)
                      .where(ASSET_SOURCE_LINK.TENANT_ID.eq(tenantId))
                      .and(ASSET_SOURCE_LINK.ASSET_ID.eq(ASSET.ID))
                      .and(ASSET_SOURCE_LINK.SOURCE_TYPE.eq(normalized(query.sourceType())))));
    }
    return condition;
  }

  private AssetResponse toResponse(
      io.aegisops.persistence.jooq.public_.tables.records.AssetRecord row) {
    int sourceCount =
        dsl.fetchCount(
            dsl.selectFrom(ASSET_SOURCE_LINK)
                .where(ASSET_SOURCE_LINK.TENANT_ID.eq(row.getTenantId()))
                .and(ASSET_SOURCE_LINK.ASSET_ID.eq(row.getId())));
    return new AssetResponse(
        row.getId(),
        row.getAssetType(),
        row.getName(),
        row.getDisplayName(),
        row.getDescription(),
        row.getEnv(),
        row.getIp(),
        row.getSite(),
        row.getOwnerTeam(),
        row.getCriticality(),
        map(row.getTags()),
        row.getStatus(),
        sourceCount,
        row.getLastSeenAt(),
        row.getCreatedAt(),
        row.getUpdatedAt(),
        row.getVersion());
  }

  private Map<String, Object> map(JSONB value) {
    if (value == null) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(value.data(), new TypeReference<>() {});
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("asset tags are not valid JSON", exception);
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String normalized(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }
}

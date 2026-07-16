package io.aegisops.asset.infrastructure.persistence;

import static io.aegisops.persistence.jooq.public_.tables.Asset.ASSET;
import static io.aegisops.persistence.jooq.public_.tables.AssetIdentity.ASSET_IDENTITY;
import static io.aegisops.persistence.jooq.public_.tables.AssetSourceLink.ASSET_SOURCE_LINK;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.asset.api.dto.AssetUpsertCommand;
import io.aegisops.asset.domain.model.Asset;
import io.aegisops.asset.domain.model.NormalizedAssetIdentity;
import io.aegisops.asset.domain.model.NormalizedAssetIdentity.Strength;
import io.aegisops.asset.domain.rule.AssetIdentityConflictException;
import io.aegisops.common.id.Ids;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqAssetRepository implements AssetRepository {
  private final DSLContext dsl;
  private final ObjectMapper objectMapper;

  public JooqAssetRepository(DSLContext dsl, ObjectMapper objectMapper) {
    this.dsl = dsl;
    this.objectMapper = objectMapper;
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

  @Override
  public Optional<String> findAssetIdBySourceLink(
      String tenantId, String sourceType, String sourceInstanceId, String externalId) {
    return dsl.select(ASSET_SOURCE_LINK.ASSET_ID)
        .from(ASSET_SOURCE_LINK)
        .where(ASSET_SOURCE_LINK.TENANT_ID.eq(tenantId))
        .and(ASSET_SOURCE_LINK.SOURCE_TYPE.eq(sourceType))
        .and(ASSET_SOURCE_LINK.SOURCE_INSTANCE_ID.eq(sourceInstanceId))
        .and(ASSET_SOURCE_LINK.EXTERNAL_ID.eq(externalId))
        .fetchOptional(ASSET_SOURCE_LINK.ASSET_ID);
  }

  @Override
  public Set<String> findAssetIdsByStrongIdentities(
      String tenantId, List<NormalizedAssetIdentity> identities) {
    Condition matches = identityMatches(identities, Strength.STRONG);
    if (matches == null) {
      return Set.of();
    }
    return new LinkedHashSet<>(
        dsl.selectDistinct(ASSET_IDENTITY.ASSET_ID)
            .from(ASSET_IDENTITY)
            .where(ASSET_IDENTITY.TENANT_ID.eq(tenantId))
            .and(ASSET_IDENTITY.STRENGTH.eq("strong"))
            .and(matches)
            .fetch(ASSET_IDENTITY.ASSET_ID));
  }

  @Override
  public boolean hasWeakIdentityConflict(
      String tenantId, List<NormalizedAssetIdentity> identities, String excludedAssetId) {
    Condition matches = identityMatches(identities, Strength.WEAK);
    if (matches == null) {
      return false;
    }
    Condition condition =
        ASSET_IDENTITY.TENANT_ID
            .eq(tenantId)
            .and(ASSET_IDENTITY.STRENGTH.eq("weak"))
            .and(matches);
    if (excludedAssetId != null) {
      condition = condition.and(ASSET_IDENTITY.ASSET_ID.ne(excludedAssetId));
    }
    return dsl.fetchExists(dsl.selectOne().from(ASSET_IDENTITY).where(condition));
  }

  @Override
  public String createAsset(AssetUpsertCommand command, OffsetDateTime now) {
    String assetId = "asset_" + Ids.newId();
    dsl.insertInto(ASSET)
        .set(ASSET.ID, assetId)
        .set(ASSET.TENANT_ID, command.tenantId())
        .set(ASSET.ASSET_TYPE, normalized(command.assetType()))
        .set(ASSET.NAME, command.name().trim())
        .set(ASSET.DISPLAY_NAME, trimmedOrNull(command.displayName()))
        .set(ASSET.DESCRIPTION, trimmedOrNull(command.description()))
        .set(ASSET.SOURCE, normalized(command.sourceType()))
        .set(ASSET.SOURCE_ID, command.externalId().trim())
        .set(ASSET.ENV, trimmedOrNull(command.environment()))
        .set(ASSET.IP, trimmedOrNull(command.ip()))
        .set(ASSET.SITE, trimmedOrNull(command.site()))
        .set(ASSET.OWNER_TEAM, trimmedOrNull(command.ownerTeam()))
        .set(ASSET.CRITICALITY, defaultCriticality(command.criticality()))
        .set(ASSET.TAGS, json(command.tags()))
        .set(ASSET.STATUS, "active")
        .set(ASSET.LAST_SEEN_AT, now)
        .set(ASSET.CREATED_AT, now)
        .set(ASSET.UPDATED_AT, now)
        .execute();
    return assetId;
  }

  @Override
  public void updateAsset(String assetId, AssetUpsertCommand command, OffsetDateTime now) {
    int updated =
        dsl.update(ASSET)
            .set(ASSET.ASSET_TYPE, normalized(command.assetType()))
            .set(ASSET.NAME, command.name().trim())
            .set(ASSET.DISPLAY_NAME, trimmedOrNull(command.displayName()))
            .set(ASSET.DESCRIPTION, trimmedOrNull(command.description()))
            .set(ASSET.ENV, trimmedOrNull(command.environment()))
            .set(ASSET.IP, trimmedOrNull(command.ip()))
            .set(ASSET.SITE, trimmedOrNull(command.site()))
            .set(ASSET.OWNER_TEAM, trimmedOrNull(command.ownerTeam()))
            .set(ASSET.CRITICALITY, defaultCriticality(command.criticality()))
            .set(ASSET.TAGS, json(command.tags()))
            .set(ASSET.LAST_SEEN_AT, now)
            .set(ASSET.UPDATED_AT, now)
            .set(ASSET.VERSION, ASSET.VERSION.plus(1L))
            .where(ASSET.TENANT_ID.eq(command.tenantId()))
            .and(ASSET.ID.eq(assetId))
            .and(ASSET.DELETED_AT.isNull())
            .execute();
    if (updated != 1) {
      throw new IllegalArgumentException("asset not found in tenant: " + assetId);
    }
  }

  @Override
  public String upsertSourceLink(
      String assetId, AssetUpsertCommand command, OffsetDateTime now) {
    String sourceType = normalized(command.sourceType());
    String sourceInstanceId = command.sourceInstanceId().trim();
    String externalId = command.externalId().trim();
    Optional<String> existing =
        dsl.select(ASSET_SOURCE_LINK.ID)
            .from(ASSET_SOURCE_LINK)
            .where(ASSET_SOURCE_LINK.TENANT_ID.eq(command.tenantId()))
            .and(ASSET_SOURCE_LINK.SOURCE_TYPE.eq(sourceType))
            .and(ASSET_SOURCE_LINK.SOURCE_INSTANCE_ID.eq(sourceInstanceId))
            .and(ASSET_SOURCE_LINK.EXTERNAL_ID.eq(externalId))
            .fetchOptional(ASSET_SOURCE_LINK.ID);
    if (existing.isPresent()) {
      dsl.update(ASSET_SOURCE_LINK)
          .set(ASSET_SOURCE_LINK.ASSET_ID, assetId)
          .set(ASSET_SOURCE_LINK.DATASOURCE_ID, trimmedOrNull(command.datasourceId()))
          .set(ASSET_SOURCE_LINK.INGESTION_CHANNEL, normalized(command.ingestionChannel()))
          .set(ASSET_SOURCE_LINK.SYNC_STATUS, "active")
          .set(ASSET_SOURCE_LINK.RAW_PAYLOAD, json(command.rawPayload()))
          .set(ASSET_SOURCE_LINK.LAST_SEEN_AT, now)
          .set(ASSET_SOURCE_LINK.UPDATED_AT, now)
          .where(ASSET_SOURCE_LINK.ID.eq(existing.orElseThrow()))
          .execute();
      return existing.orElseThrow();
    }

    String sourceLinkId = "asrc_" + Ids.newId();
    dsl.insertInto(ASSET_SOURCE_LINK)
        .set(ASSET_SOURCE_LINK.ID, sourceLinkId)
        .set(ASSET_SOURCE_LINK.TENANT_ID, command.tenantId())
        .set(ASSET_SOURCE_LINK.ASSET_ID, assetId)
        .set(ASSET_SOURCE_LINK.SOURCE_TYPE, sourceType)
        .set(ASSET_SOURCE_LINK.SOURCE_INSTANCE_ID, sourceInstanceId)
        .set(ASSET_SOURCE_LINK.DATASOURCE_ID, trimmedOrNull(command.datasourceId()))
        .set(ASSET_SOURCE_LINK.EXTERNAL_ID, externalId)
        .set(ASSET_SOURCE_LINK.INGESTION_CHANNEL, normalized(command.ingestionChannel()))
        .set(ASSET_SOURCE_LINK.SYNC_STATUS, "active")
        .set(ASSET_SOURCE_LINK.RAW_PAYLOAD, json(command.rawPayload()))
        .set(ASSET_SOURCE_LINK.FIRST_SEEN_AT, now)
        .set(ASSET_SOURCE_LINK.LAST_SEEN_AT, now)
        .set(ASSET_SOURCE_LINK.CREATED_AT, now)
        .set(ASSET_SOURCE_LINK.UPDATED_AT, now)
        .execute();
    return sourceLinkId;
  }

  @Override
  public void replaceSourceIdentities(
      String assetId,
      String sourceLinkId,
      String tenantId,
      List<NormalizedAssetIdentity> identities,
      OffsetDateTime now) {
    dsl.deleteFrom(ASSET_IDENTITY)
        .where(ASSET_IDENTITY.TENANT_ID.eq(tenantId))
        .and(ASSET_IDENTITY.SOURCE_LINK_ID.eq(sourceLinkId))
        .and(ASSET_IDENTITY.STRENGTH.eq("weak"))
        .execute();

    for (NormalizedAssetIdentity identity : identities) {
      if (identity.strength() == Strength.STRONG
          && refreshExistingStrongIdentity(assetId, tenantId, identity, now)) {
        continue;
      }
      dsl.insertInto(ASSET_IDENTITY)
          .set(ASSET_IDENTITY.ID, "aid_" + Ids.newId())
          .set(ASSET_IDENTITY.TENANT_ID, tenantId)
          .set(ASSET_IDENTITY.ASSET_ID, assetId)
          .set(ASSET_IDENTITY.SOURCE_LINK_ID, sourceLinkId)
          .set(ASSET_IDENTITY.IDENTITY_TYPE, identity.identityType())
          .set(ASSET_IDENTITY.SCOPE_KEY, identity.scopeKey())
          .set(ASSET_IDENTITY.IDENTITY_VALUE, identity.identityValue())
          .set(ASSET_IDENTITY.NORMALIZED_VALUE, identity.normalizedValue())
          .set(ASSET_IDENTITY.STRENGTH, identity.strength().name().toLowerCase(Locale.ROOT))
          .set(ASSET_IDENTITY.VERIFIED, identity.verified())
          .set(ASSET_IDENTITY.CREATED_AT, now)
          .set(ASSET_IDENTITY.UPDATED_AT, now)
          .execute();
    }
  }

  private boolean refreshExistingStrongIdentity(
      String assetId,
      String tenantId,
      NormalizedAssetIdentity identity,
      OffsetDateTime now) {
    var existing =
        dsl.select(ASSET_IDENTITY.ID, ASSET_IDENTITY.ASSET_ID, ASSET_IDENTITY.VERIFIED)
            .from(ASSET_IDENTITY)
            .where(ASSET_IDENTITY.TENANT_ID.eq(tenantId))
            .and(ASSET_IDENTITY.IDENTITY_TYPE.eq(identity.identityType()))
            .and(ASSET_IDENTITY.SCOPE_KEY.eq(identity.scopeKey()))
            .and(ASSET_IDENTITY.NORMALIZED_VALUE.eq(identity.normalizedValue()))
            .and(ASSET_IDENTITY.STRENGTH.eq("strong"))
            .fetchOptional();
    if (existing.isEmpty()) {
      return false;
    }
    if (!assetId.equals(existing.orElseThrow().get(ASSET_IDENTITY.ASSET_ID))) {
      throw new AssetIdentityConflictException("strong identity already belongs to another asset");
    }
    dsl.update(ASSET_IDENTITY)
        .set(ASSET_IDENTITY.IDENTITY_VALUE, identity.identityValue())
        .set(
            ASSET_IDENTITY.VERIFIED,
            Boolean.TRUE.equals(existing.orElseThrow().get(ASSET_IDENTITY.VERIFIED))
                || identity.verified())
        .set(ASSET_IDENTITY.UPDATED_AT, now)
        .where(ASSET_IDENTITY.ID.eq(existing.orElseThrow().get(ASSET_IDENTITY.ID)))
        .execute();
    return true;
  }

  private Condition identityMatches(
      List<NormalizedAssetIdentity> identities, Strength expectedStrength) {
    Condition matches = null;
    for (NormalizedAssetIdentity identity : identities) {
      if (identity.strength() != expectedStrength) {
        continue;
      }
      Condition current =
          ASSET_IDENTITY.IDENTITY_TYPE
              .eq(identity.identityType())
              .and(ASSET_IDENTITY.SCOPE_KEY.eq(identity.scopeKey()))
              .and(ASSET_IDENTITY.NORMALIZED_VALUE.eq(identity.normalizedValue()));
      matches = matches == null ? current : matches.or(current);
    }
    return matches;
  }

  private JSONB json(Object value) {
    try {
      return JSONB.valueOf(objectMapper.writeValueAsString(value));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("asset payload is not serializable", e);
    }
  }

  private String normalized(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private String trimmedOrNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String defaultCriticality(String value) {
    return value == null || value.isBlank() ? "normal" : normalized(value);
  }
}

package io.aegisops.asset.application;

import io.aegisops.asset.api.dto.AssetUpsertCommand;
import io.aegisops.asset.api.dto.AssetUpsertResult;
import io.aegisops.asset.domain.model.NormalizedAssetIdentity;
import io.aegisops.asset.domain.model.NormalizedAssetIdentity.Strength;
import io.aegisops.asset.domain.rule.AssetIdentityNormalizer;
import io.aegisops.asset.domain.rule.AssetIdentityResolver;
import io.aegisops.asset.infrastructure.persistence.AssetRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetApplicationService {
  private final AssetRepository repository;
  private final AssetIdentityNormalizer normalizer;
  private final AssetIdentityResolver resolver;

  public AssetApplicationService(
      AssetRepository repository,
      AssetIdentityNormalizer normalizer,
      AssetIdentityResolver resolver) {
    this.repository = repository;
    this.normalizer = normalizer;
    this.resolver = resolver;
  }

  @Transactional
  public AssetUpsertResult upsert(AssetUpsertCommand command) {
    validate(command);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    List<NormalizedAssetIdentity> identities =
        normalizer.normalize(command.identities(), command.ip());
    List<NormalizedAssetIdentity> strongIdentities =
        identities.stream().filter(identity -> identity.strength() == Strength.STRONG).toList();
    List<NormalizedAssetIdentity> weakIdentities =
        identities.stream().filter(identity -> identity.strength() == Strength.WEAK).toList();

    var sourceAssetId =
        repository.findAssetIdBySourceLink(
            command.tenantId(),
            normalized(command.sourceType()),
            command.sourceInstanceId().trim(),
            command.externalId().trim());
    var strongAssetIds =
        repository.findAssetIdsByStrongIdentities(command.tenantId(), strongIdentities);
    var resolution = resolver.resolve(sourceAssetId, strongAssetIds, false);
    String assetId;
    if (resolution.action() == AssetIdentityResolver.Action.CREATE) {
      assetId = repository.createAsset(command, now);
    } else {
      assetId = resolution.assetId().orElseThrow();
      repository.updateAsset(assetId, command, now);
    }

    boolean weakConflict =
        repository.hasWeakIdentityConflict(
            command.tenantId(),
            weakIdentities,
            resolution.action() == AssetIdentityResolver.Action.CREATE ? null : assetId);
    String sourceLinkId = repository.upsertSourceLink(assetId, command, now);
    repository.replaceSourceIdentities(
        assetId, sourceLinkId, command.tenantId(), identities, now);

    return new AssetUpsertResult(
        assetId, sourceLinkId, actionLabel(resolution.action()), weakConflict);
  }

  private String actionLabel(AssetIdentityResolver.Action action) {
    return switch (action) {
      case CREATE -> "created";
      case UPDATE -> "updated";
      case LINK -> "linked";
    };
  }

  private void validate(AssetUpsertCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("asset upsert command is required");
    }
    requireText(command.tenantId(), "tenantId");
    requireText(command.assetType(), "assetType");
    requireText(command.name(), "name");
    requireText(command.sourceType(), "sourceType");
    requireText(command.sourceInstanceId(), "sourceInstanceId");
    requireText(command.externalId(), "externalId");
    requireText(command.ingestionChannel(), "ingestionChannel");
  }

  private void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }

  private String normalized(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }
}

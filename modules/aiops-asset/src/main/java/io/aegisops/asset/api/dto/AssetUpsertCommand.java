package io.aegisops.asset.api.dto;

import io.aegisops.asset.domain.model.AssetIdentityInput;
import java.util.List;
import java.util.Map;

public record AssetUpsertCommand(
    String tenantId,
    String assetType,
    String name,
    String displayName,
    String description,
    String environment,
    String site,
    String ownerTeam,
    String criticality,
    String ip,
    Map<String, Object> tags,
    String sourceType,
    String sourceInstanceId,
    String datasourceId,
    String externalId,
    String ingestionChannel,
    Map<String, Object> rawPayload,
    List<AssetIdentityInput> identities) {

  public AssetUpsertCommand {
    tags = tags == null ? Map.of() : Map.copyOf(tags);
    rawPayload = rawPayload == null ? Map.of() : Map.copyOf(rawPayload);
    identities = identities == null ? List.of() : List.copyOf(identities);
  }
}

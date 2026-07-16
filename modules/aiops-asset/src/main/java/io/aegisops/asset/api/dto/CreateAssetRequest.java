package io.aegisops.asset.api.dto;

import io.aegisops.asset.domain.model.AssetIdentityInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record CreateAssetRequest(
    @NotBlank @Size(max = 64) String assetType,
    @NotBlank @Size(max = 256) String name,
    @Size(max = 256) String displayName,
    @Size(max = 4000) String description,
    @Size(max = 64) String environment,
    @Size(max = 64) String ip,
    @Size(max = 128) String site,
    @Size(max = 128) String ownerTeam,
    @Size(max = 32) String criticality,
    Map<String, Object> tags,
    List<AssetIdentityInput> identities) {

  public CreateAssetRequest {
    tags = tags == null ? Map.of() : Map.copyOf(tags);
    identities = identities == null ? List.of() : List.copyOf(identities);
  }
}

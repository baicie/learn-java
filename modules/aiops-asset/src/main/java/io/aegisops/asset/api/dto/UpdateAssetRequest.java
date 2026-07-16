package io.aegisops.asset.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record UpdateAssetRequest(
    @NotBlank @Size(max = 64) String assetType,
    @NotBlank @Size(max = 256) String name,
    @Size(max = 256) String displayName,
    @Size(max = 4000) String description,
    @Size(max = 64) String environment,
    @Size(max = 64) String ip,
    @Size(max = 128) String site,
    @Size(max = 128) String ownerTeam,
    @Size(max = 32) String criticality,
    @Size(max = 32) String status,
    Map<String, Object> tags,
    @NotNull @PositiveOrZero Long version) {

  public UpdateAssetRequest {
    tags = tags == null ? Map.of() : Map.copyOf(tags);
  }
}

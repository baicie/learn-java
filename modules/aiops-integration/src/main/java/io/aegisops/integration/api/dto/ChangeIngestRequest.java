package io.aegisops.integration.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ChangeIngestRequest(
    @NotBlank String sourceEventId,
    @NotBlank String serviceName,
    @NotBlank String changeType,
    @NotBlank String title,
    String description,
    String operator,
    String riskLevel,
    @NotNull OffsetDateTime occurredAt,
    Map<String, Object> attributes,
    String ownerTeam,
    String repositoryUrl,
    String runbookId,
    List<String> dependencyAssetIds) {
  public ChangeIngestRequest {
    attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    dependencyAssetIds = dependencyAssetIds == null ? List.of() : List.copyOf(dependencyAssetIds);
  }
}

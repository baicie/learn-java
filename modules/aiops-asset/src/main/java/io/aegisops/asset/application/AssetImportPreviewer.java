package io.aegisops.asset.application;

import io.aegisops.asset.domain.model.AssetIdentityInput;
import io.aegisops.asset.domain.model.NormalizedAssetIdentity.Strength;
import io.aegisops.asset.domain.rule.AssetCsvRowValidator;
import io.aegisops.asset.domain.rule.AssetIdentityNormalizer;
import io.aegisops.asset.infrastructure.adapter.AssetCsvParser;
import io.aegisops.asset.infrastructure.adapter.AssetCsvRow;
import io.aegisops.asset.infrastructure.persistence.AssetImportRowDraft;
import io.aegisops.asset.infrastructure.persistence.AssetRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class AssetImportPreviewer {
  private final AssetCsvParser parser;
  private final AssetCsvRowValidator validator;
  private final AssetIdentityNormalizer identityNormalizer;
  private final AssetRepository assetRepository;

  AssetImportPreviewer(
      AssetCsvParser parser,
      AssetCsvRowValidator validator,
      AssetIdentityNormalizer identityNormalizer,
      AssetRepository assetRepository) {
    this.parser = parser;
    this.validator = validator;
    this.identityNormalizer = identityNormalizer;
    this.assetRepository = assetRepository;
  }

  List<AssetImportRowDraft> preview(String tenantId, String sourceInstanceId, byte[] content) {
    List<AssetCsvRow> rows = parser.parse(content);
    Map<String, Integer> externalIds = new LinkedHashMap<>();
    for (AssetCsvRow row : rows) {
      if (!row.externalId().isBlank()) {
        externalIds.merge(row.externalId(), 1, Integer::sum);
      }
    }
    return rows.stream()
        .map(row -> previewRow(tenantId, sourceInstanceId, row, externalIds))
        .toList();
  }

  private AssetImportRowDraft previewRow(
      String tenantId, String sourceInstanceId, AssetCsvRow row, Map<String, Integer> externalIds) {
    List<String> errors = new ArrayList<>(validator.validate(row));
    if (externalIds.getOrDefault(row.externalId(), 0) > 1) {
      errors.add("EXTERNAL_ID_DUPLICATE");
    }
    String status = errors.isEmpty() ? "valid" : "invalid";
    String action = null;
    if (errors.isEmpty()) {
      var identities = identityNormalizer.normalize(identityInputs(row), blankToNull(row.ip()));
      Set<String> strongMatches =
          assetRepository.findAssetIdsByStrongIdentities(
              tenantId,
              identities.stream()
                  .filter(identity -> identity.strength() == Strength.STRONG)
                  .toList());
      var sourceMatch =
          assetRepository.findAssetIdBySourceLink(
              tenantId, "csv", sourceInstanceId, row.externalId());
      if (strongMatches.size() > 1
          || (sourceMatch.isPresent()
              && !strongMatches.isEmpty()
              && !strongMatches.contains(sourceMatch.orElseThrow()))) {
        status = "conflict";
        errors.add("STRONG_IDENTITY_CONFLICT");
      } else if (sourceMatch.isPresent()) {
        action = "update";
      } else if (!strongMatches.isEmpty()) {
        action = "link";
      } else {
        action = "create";
      }
    }
    return new AssetImportRowDraft(
        row.rowNumber(), row.externalId(), payload(row), status, action, List.copyOf(errors));
  }

  private Map<String, Object> payload(AssetCsvRow row) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("externalId", row.externalId());
    payload.put("assetType", row.assetType().toLowerCase(Locale.ROOT));
    payload.put("name", row.name());
    payload.put("displayName", row.displayName());
    payload.put("environment", row.environment());
    payload.put("site", row.site());
    payload.put("ownerTeam", row.ownerTeam());
    payload.put("criticality", row.criticality());
    payload.put("ip", row.ip());
    payload.put("machineId", row.machineId());
    payload.put("cloudInstanceId", row.cloudInstanceId());
    payload.put("k8sUid", row.k8sUid());
    payload.put("tags", row.tags());
    return Map.copyOf(payload);
  }

  private List<AssetIdentityInput> identityInputs(AssetCsvRow row) {
    List<AssetIdentityInput> result = new ArrayList<>();
    addIdentity(result, "machine_id", row.machineId());
    addIdentity(result, "cloud_instance_id", row.cloudInstanceId());
    addIdentity(result, "k8s_uid", row.k8sUid());
    return List.copyOf(result);
  }

  private void addIdentity(List<AssetIdentityInput> identities, String type, String value) {
    if (value != null && !value.isBlank()) {
      identities.add(new AssetIdentityInput(type, "global", value, true));
    }
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}

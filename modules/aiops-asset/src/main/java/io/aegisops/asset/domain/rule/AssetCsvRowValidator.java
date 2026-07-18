package io.aegisops.asset.domain.rule;

import io.aegisops.asset.domain.model.AssetCsvRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AssetCsvRowValidator {
  private static final Set<String> ASSET_TYPES =
      Set.of(
          "host",
          "service",
          "application",
          "database",
          "network_device",
          "cloud_instance",
          "k8s_cluster",
          "k8s_node",
          "k8s_namespace",
          "k8s_workload",
          "k8s_pod");

  public List<String> validate(AssetCsvRow row) {
    List<String> errors = new ArrayList<>();
    required(row.externalId(), "EXTERNAL_ID_REQUIRED", errors);
    required(row.assetType(), "ASSET_TYPE_REQUIRED", errors);
    required(row.name(), "NAME_REQUIRED", errors);
    if (!row.assetType().isBlank() && !ASSET_TYPES.contains(row.assetType().toLowerCase())) {
      errors.add("ASSET_TYPE_INVALID");
    }
    if (row.externalId().length() > 256) {
      errors.add("EXTERNAL_ID_TOO_LONG");
    }
    if (row.name().length() > 256) {
      errors.add("NAME_TOO_LONG");
    }
    return List.copyOf(errors);
  }

  private void required(String value, String code, List<String> errors) {
    if (value == null || value.isBlank()) {
      errors.add(code);
    }
  }
}

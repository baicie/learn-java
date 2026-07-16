package io.aegisops.asset.domain.rule;

import io.aegisops.asset.domain.model.AssetIdentityInput;
import io.aegisops.asset.domain.model.NormalizedAssetIdentity;
import io.aegisops.asset.domain.model.NormalizedAssetIdentity.Strength;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AssetIdentityNormalizer {
  private static final Set<String> STRONG_TYPES =
      Set.of(
          "cloud_instance_id",
          "cmdb_ci_id",
          "machine_id",
          "k8s_uid",
          "otel_service_instance_id");
  private static final Set<String> WEAK_TYPES = Set.of("fqdn", "hostname", "ip", "display_name");

  public List<NormalizedAssetIdentity> normalize(
      List<AssetIdentityInput> inputs, String ipAddress) {
    List<AssetIdentityInput> all = new ArrayList<>(inputs == null ? List.of() : inputs);
    if (hasText(ipAddress)
        && all.stream().noneMatch(input -> "ip".equals(normalizeType(input.identityType())))) {
      all.add(new AssetIdentityInput("ip", "global", ipAddress, false));
    }

    Map<String, NormalizedAssetIdentity> distinct = new LinkedHashMap<>();
    for (AssetIdentityInput input : all) {
      if (input == null || !hasText(input.identityValue())) {
        continue;
      }
      String type = normalizeType(input.identityType());
      Strength strength = strength(type);
      String scope = hasText(input.scopeKey()) ? input.scopeKey().trim().toLowerCase(Locale.ROOT) : "global";
      String original = input.identityValue().trim();
      String normalized = original.toLowerCase(Locale.ROOT);
      var identity =
          new NormalizedAssetIdentity(
              type, scope, original, normalized, strength, input.verified());
      distinct.put(type + "\u0000" + scope + "\u0000" + normalized, identity);
    }
    return List.copyOf(distinct.values());
  }

  private Strength strength(String type) {
    if (STRONG_TYPES.contains(type)) {
      return Strength.STRONG;
    }
    if (WEAK_TYPES.contains(type)) {
      return Strength.WEAK;
    }
    throw new IllegalArgumentException("unsupported asset identity type: " + type);
  }

  private String normalizeType(String type) {
    return hasText(type) ? type.trim().toLowerCase(Locale.ROOT) : "";
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}

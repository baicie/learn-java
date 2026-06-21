package io.aegisops.datasource.zabbix;

import java.util.Locale;

public final class ZabbixExternalIds {
  private ZabbixExternalIds() {}

  public static String sourceId(String datasourceId, String externalId) {
    String normalizedDatasourceId = requireNonBlank(datasourceId, "datasourceId");
    String normalizedExternalId = requireNonBlank(externalId, "externalId");
    return normalizedDatasourceId + ":" + normalizedExternalId;
  }

  public static String fingerprint(String datasourceId, String fingerprintKey) {
    return "zabbix:" + sourceId(datasourceId, fingerprintKey);
  }

  public static String normalizeEntityType(String value) {
    if (value == null || value.isBlank()) {
      return "host";
    }

    String normalized = value.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "host", "service", "endpoint", "application" -> normalized;
      default -> "host";
    };
  }

  private static String requireNonBlank(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value.trim();
  }
}

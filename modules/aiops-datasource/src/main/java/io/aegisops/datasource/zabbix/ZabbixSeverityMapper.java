package io.aegisops.datasource.zabbix;

import java.util.Locale;

public final class ZabbixSeverityMapper {
  private ZabbixSeverityMapper() {}

  public static String map(Object severity) {
    if (severity == null) {
      return "warning";
    }

    String value = String.valueOf(severity).trim().toLowerCase(Locale.ROOT);

    if (value.isBlank()) {
      return "warning";
    }

    return switch (value) {
      case "0", "not_classified", "not classified", "information", "info" -> "info";
      case "1", "2", "warning" -> "warning";
      case "3", "average" -> "medium";
      case "4", "high" -> "high";
      case "5", "disaster", "critical" -> "critical";
      default -> {
        if (value.contains("disaster") || value.contains("critical")) {
          yield "critical";
        }
        if (value.contains("high")) {
          yield "high";
        }
        if (value.contains("average") || value.contains("medium")) {
          yield "medium";
        }
        if (value.contains("info")) {
          yield "info";
        }
        yield "warning";
      }
    };
  }
}

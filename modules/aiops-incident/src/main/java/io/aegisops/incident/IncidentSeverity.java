package io.aegisops.incident;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

public final class IncidentSeverity {
  private static final Map<String, Integer> WEIGHTS =
      Map.of(
          "info", 10,
          "low", 20,
          "warning", 30,
          "critical", 40,
          "disaster", 50);

  private IncidentSeverity() {}

  public static String normalize(String severity) {
    if (severity == null || severity.isBlank()) {
      return "info";
    }

    String normalized = severity.trim().toLowerCase(Locale.ROOT);
    return WEIGHTS.containsKey(normalized) ? normalized : "info";
  }

  public static int weight(String severity) {
    return WEIGHTS.getOrDefault(normalize(severity), 10);
  }

  public static String max(String left, String right) {
    return weight(left) >= weight(right) ? normalize(left) : normalize(right);
  }

  public static String max(Collection<String> severities) {
    return severities.stream()
        .filter(severity -> severity != null && !severity.isBlank())
        .map(IncidentSeverity::normalize)
        .max(Comparator.comparingInt(IncidentSeverity::weight))
        .orElse("info");
  }
}

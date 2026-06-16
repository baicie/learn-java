package io.aegisops.evidence;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.evidence.victoria")
public record VictoriaMetricsProperties(
    Boolean enabled,
    String baseUrl,
    Integer timeoutMillis,
    String step,
    Map<String, String> queries) {
  public boolean enabledOrFalse() {
    return Boolean.TRUE.equals(enabled);
  }

  public String normalizedBaseUrl() {
    if (baseUrl == null || baseUrl.isBlank()) {
      return "";
    }

    String value = baseUrl.trim();
    while (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    return value;
  }

  public int normalizedTimeoutMillis() {
    return timeoutMillis == null || timeoutMillis <= 0 ? 3000 : timeoutMillis;
  }

  public String normalizedStep() {
    return step == null || step.isBlank() ? "60s" : step.trim();
  }

  public Map<String, String> normalizedQueries() {
    if (queries == null || queries.isEmpty()) {
      return Map.of(
          "cpu_usage",
          "avg_over_time(node_cpu_seconds_total{asset_id=\"${assetId}\"}[5m])",
          "memory_usage",
          "avg_over_time(node_memory_MemAvailable_bytes{asset_id=\"${assetId}\"}[5m])",
          "service_latency",
          "avg_over_time(http_request_duration_seconds{asset_id=\"${assetId}\"}[5m])");
    }

    return queries;
  }
}

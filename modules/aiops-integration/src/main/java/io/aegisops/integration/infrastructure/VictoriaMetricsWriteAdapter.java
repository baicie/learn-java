package io.aegisops.integration.infrastructure;

import io.aegisops.common.exception.AppException;
import io.aegisops.otel.application.MetricWritePort;
import io.aegisops.otel.domain.model.OtelSignal;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "aiops.evidence.victoria", name = "enabled", havingValue = "true")
public class VictoriaMetricsWriteAdapter implements MetricWritePort {
  private final RestClient client;
  private final String baseUrl;

  public VictoriaMetricsWriteAdapter(
      RestClient.Builder builder, @Value("${aiops.evidence.victoria.base-url:}") String baseUrl) {
    this.client = builder.build();
    this.baseUrl = normalize(baseUrl);
  }

  @Override
  public void write(String tenantId, String assetId, OtelSignal signal) {
    if (baseUrl.isBlank()) {
      throw new AppException("METRIC_STORE_UNAVAILABLE", "VictoriaMetrics base URL is empty");
    }
    client
        .post()
        .uri(URI.create(baseUrl + "/api/v1/import/prometheus"))
        .contentType(MediaType.TEXT_PLAIN)
        .body(prometheusLine(tenantId, assetId, signal))
        .retrieve()
        .toBodilessEntity();
  }

  static String prometheusLine(String tenantId, String assetId, OtelSignal signal) {
    String name = signal.metricName().replaceAll("[^a-zA-Z0-9_:]", "_");
    return name
        + "{tenant_id=\""
        + escape(tenantId)
        + "\",asset_id=\""
        + escape(assetId)
        + "\",service_name=\""
        + escape(signal.serviceName())
        + "\"} "
        + signal.metricValue().toPlainString()
        + " "
        + signal.occurredAt().toInstant().toEpochMilli()
        + "\n";
  }

  private static String normalize(String value) {
    String normalized = value == null ? "" : value.trim();
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }
}

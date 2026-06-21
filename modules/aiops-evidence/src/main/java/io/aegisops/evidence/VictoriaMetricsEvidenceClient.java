package io.aegisops.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.MetricEvidence;
import io.aegisops.evidence.dto.MetricSeriesSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@ConditionalOnProperty(prefix = "aiops.evidence.victoria", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(VictoriaMetricsProperties.class)
public class VictoriaMetricsEvidenceClient implements MetricsEvidenceClient {
  private final VictoriaMetricsProperties properties;
  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate;

  public VictoriaMetricsEvidenceClient(
      VictoriaMetricsProperties properties, ObjectMapper objectMapper) {
    this(properties, objectMapper, createRestTemplate(properties));
  }

  VictoriaMetricsEvidenceClient(
      VictoriaMetricsProperties properties, ObjectMapper objectMapper, RestTemplate restTemplate) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.restTemplate = restTemplate;
  }

  @Override
  public MetricEvidence queryMetrics(EvidenceQueryRequest request) {
    if (!properties.enabledOrFalse()) {
      return MetricEvidence.unavailable("VictoriaMetrics evidence client is disabled.");
    }

    if (properties.normalizedBaseUrl().isBlank()) {
      return MetricEvidence.unavailable("VictoriaMetrics base URL is empty.");
    }

    if (request.primaryAssetId() == null || request.primaryAssetId().isBlank()) {
      return MetricEvidence.unavailable("Primary asset id is empty.");
    }

    List<MetricSeriesSummary> summaries = new ArrayList<>();

    for (Map.Entry<String, String> entry : properties.normalizedQueries().entrySet()) {
      String metricName = entry.getKey();
      String query = entry.getValue().replace("${assetId}", request.primaryAssetId());

      try {
        MetricSeriesSummary summary =
            queryRange(metricName, query, request.startedAt(), request.lastSeenAt());
        if (summary != null) {
          summaries.add(summary);
        }
      } catch (Exception ignored) {
        // Evidence must never break diagnosis. Individual query failures are ignored.
      }
    }

    if (summaries.isEmpty()) {
      return MetricEvidence.unavailable("No metric evidence returned.");
    }

    return new MetricEvidence(true, "", summaries);
  }

  private MetricSeriesSummary queryRange(
      String metricName, String query, OffsetDateTime startedAt, OffsetDateTime lastSeenAt)
      throws Exception {
    String url =
        UriComponentsBuilder.fromHttpUrl(properties.normalizedBaseUrl() + "/api/v1/query_range")
            .queryParam("query", query)
            .queryParam("start", startedAt.toInstant().getEpochSecond())
            .queryParam("end", lastSeenAt.toInstant().getEpochSecond())
            .queryParam("step", properties.normalizedStep())
            .toUriString();

    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
    JsonNode root = objectMapper.readTree(response.getBody());

    JsonNode result = root.path("data").path("result");
    if (!result.isArray() || result.isEmpty()) {
      return null;
    }

    List<BigDecimal> values = new ArrayList<>();

    for (JsonNode series : result) {
      JsonNode points = series.path("values");
      if (!points.isArray()) {
        continue;
      }

      for (JsonNode point : points) {
        if (point.isArray() && point.size() >= 2) {
          String raw = point.get(1).asText();
          try {
            values.add(new BigDecimal(raw));
          } catch (NumberFormatException ignored) {
            // skip invalid data points
          }
        }
      }
    }

    if (values.isEmpty()) {
      return null;
    }

    BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    BigDecimal max = values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal avg = sum.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    BigDecimal latest = values.get(values.size() - 1);

    return new MetricSeriesSummary(metricName, query, min, max, avg, latest, values.size());
  }

  private static RestTemplate createRestTemplate(VictoriaMetricsProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.normalizedTimeoutMillis());
    factory.setReadTimeout(properties.normalizedTimeoutMillis());
    return new RestTemplate(factory);
  }
}

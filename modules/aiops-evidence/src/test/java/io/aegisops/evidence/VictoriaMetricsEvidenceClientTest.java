package io.aegisops.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.MetricEvidence;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class VictoriaMetricsEvidenceClientTest {
  @Test
  void returnsMetricEvidenceFromQueryRange() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server =
        MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();

    server
        .expect(
            request -> assertTrue(request.getURI().toString().contains("/api/v1/query_range")))
        .andRespond(
            withSuccess(
                """
                {
                  "status": "success",
                  "data": {
                    "result": [
                      {
                        "metric": {"instance": "asset_1"},
                        "values": [
                          [1780000000, "0.1"],
                          [1780000060, "0.5"],
                          [1780000120, "0.9"]
                        ]
                      }
                    ]
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    VictoriaMetricsProperties properties =
        new VictoriaMetricsProperties(
            true,
            "http://victoria:8428",
            1000,
            "60s",
            Map.of("cpu_usage", "cpu{asset_id=\"${assetId}\"}"));

    VictoriaMetricsEvidenceClient client =
        new VictoriaMetricsEvidenceClient(properties, new ObjectMapper(), restTemplate);

    MetricEvidence evidence =
        client.queryMetrics(
            new EvidenceQueryRequest(
                "agent-diagnosis.v1",
                "tenant_1",
                "inc_1",
                "trace_1",
                "asset_1",
                OffsetDateTime.parse("2026-06-16T10:00:00+09:00"),
                OffsetDateTime.parse("2026-06-16T10:10:00+09:00"),
                List.of(),
                List.of()));

    assertTrue(evidence.available());
    assertEquals(1, evidence.series().size());
    assertEquals("cpu_usage", evidence.series().get(0).name());
    assertEquals(3, evidence.series().get(0).points());

    server.verify();
  }

  @Test
  void returnsUnavailableWhenDisabled() {
    VictoriaMetricsEvidenceClient client =
        new VictoriaMetricsEvidenceClient(
            new VictoriaMetricsProperties(false, "", 1000, "60s", Map.of()),
            new ObjectMapper(),
            new RestTemplate());

    MetricEvidence evidence =
        client.queryMetrics(
            new EvidenceQueryRequest(
                "agent-diagnosis.v1",
                "tenant_1",
                "inc_1",
                "trace_1",
                "asset_1",
                OffsetDateTime.now().minusMinutes(10),
                OffsetDateTime.now(),
                List.of(),
                List.of()));

    assertFalse(evidence.available());
  }
}

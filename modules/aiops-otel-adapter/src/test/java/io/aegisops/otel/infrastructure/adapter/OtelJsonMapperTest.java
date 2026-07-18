package io.aegisops.otel.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OtelJsonMapperTest {
  @Test
  void mapsOtlpResourceAttributes() throws Exception {
    var node =
        new ObjectMapper()
            .readTree(
                """
        {"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"orders"}},{"key":"service.version","value":{"stringValue":"1.2"}},{"key":"deployment.environment","value":{"stringValue":"prod"}},{"key":"service.instance.id","value":{"stringValue":"orders-1"}}]},"signal":{"signalType":"trace","sourceId":"span-1","traceId":"trace-1"}}
        """);
    var signal = new OtelJsonMapper(new ObjectMapper()).map(node);
    assertEquals("orders", signal.serviceName());
    assertEquals("1.2", signal.serviceVersion());
    assertEquals("prod", signal.environment());
    assertEquals("orders-1", signal.serviceInstanceId());
  }

  @Test
  void mapsStandardOtlpTraceLogAndMetricEnvelopes() throws Exception {
    var json = new ObjectMapper();
    var mapper = new OtelJsonMapper(json);
    var traces =
        mapper.mapAll(
            json.readTree(
                """
        {"resourceSpans":[{"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"orders"}}]},"scopeSpans":[{"spans":[{"traceId":"trace-1","spanId":"span-1","name":"GET /orders","startTimeUnixNano":"1784282400000000000"}]}]}]}
        """));
    var logs =
        mapper.mapAll(
            json.readTree(
                """
        {"resourceLogs":[{"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"orders"}}]},"scopeLogs":[{"logRecords":[{"traceId":"trace-1","severityText":"ERROR","body":{"stringValue":"boom"},"timeUnixNano":"1784282400000000000"}]}]}]}
        """));
    var metrics =
        mapper.mapAll(
            json.readTree(
                """
        {"resourceMetrics":[{"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"orders"}}]},"scopeMetrics":[{"metrics":[{"name":"http.server.duration","gauge":{"dataPoints":[{"asDouble":12.5,"timeUnixNano":"1784282400000000000"}]}}]}]}]}
        """));

    assertEquals("trace", traces.getFirst().signalType());
    assertEquals("boom", logs.getFirst().message());
    assertEquals(new java.math.BigDecimal("12.5"), metrics.getFirst().metricValue());
  }
}

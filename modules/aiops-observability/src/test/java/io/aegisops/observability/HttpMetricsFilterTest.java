package io.aegisops.observability;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class HttpMetricsFilterTest {
  @Test
  void recordsHttpCounterAndTimer() throws Exception {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    ObservabilityProperties properties = new ObservabilityProperties();
    properties.setServiceName("aiops-server");

    HttpMetricsFilter filter = new HttpMetricsFilter(registry, properties);

    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/api/incidents/inc_123456789012");
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(200);

    filter.doFilter(request, response, (req, res) -> {});

    assertNotNull(registry.find("aegisops_http_requests_total").counter());
    assertNotNull(registry.find("aegisops_http_request_duration").timer());
  }

  @Test
  void skipsActuatorPath() throws Exception {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    ObservabilityProperties properties = new ObservabilityProperties();

    HttpMetricsFilter filter = new HttpMetricsFilter(registry, properties);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertNull(registry.find("aegisops_http_requests_total").counter());
  }
}

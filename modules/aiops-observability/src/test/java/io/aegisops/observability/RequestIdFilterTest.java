package io.aegisops.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {
  @Test
  void usesProvidedRequestIdAndClearsMdc() throws Exception {
    ObservabilityProperties properties = new ObservabilityProperties();
    properties.setServiceName("aiops-server");

    RequestIdFilter filter = new RequestIdFilter(properties);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
    request.addHeader("X-Request-Id", "req_test");

    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (req, res) -> assertEquals("req_test", MDC.get(ObservabilityConstants.MDC_REQUEST_ID)));

    assertEquals("req_test", response.getHeader("X-Request-Id"));
    assertEquals("req_test", response.getHeader("X-Trace-Id"));
    assertNull(MDC.get(ObservabilityConstants.MDC_REQUEST_ID));
  }

  @Test
  void generatesRequestIdWhenMissing() throws Exception {
    ObservabilityProperties properties = new ObservabilityProperties();
    RequestIdFilter filter = new RequestIdFilter(properties);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    String requestId = response.getHeader("X-Request-Id");
    assertTrue(requestId != null && requestId.startsWith("req_"));
  }
}

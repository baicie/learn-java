package io.aegisops.observability;

import static org.assertj.core.api.Assertions.assertThat;
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

  @Test
  void rejectsClientRequestIdContainingNewline() throws Exception {
    ObservabilityProperties properties = new ObservabilityProperties();
    RequestIdFilter filter = new RequestIdFilter(properties);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
    request.addHeader("X-Request-Id", "evil\nLOG_INJECTION");

    MockHttpServletResponse response = new MockHttpServletResponse();

    final String[] seen = {null};

    filter.doFilter(
        request, response, (req, res) -> seen[0] = MDC.get(ObservabilityConstants.MDC_REQUEST_ID));

    assertThat(seen[0]).startsWith("req_");
    assertThat(response.getHeader("X-Request-Id")).startsWith("req_");
    assertThat(response.getHeader("X-Request-Id")).doesNotContain("evil");
  }

  @Test
  void rejectsClientRequestIdContainingCarriageReturn() throws Exception {
    ObservabilityProperties properties = new ObservabilityProperties();
    RequestIdFilter filter = new RequestIdFilter(properties);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
    request.addHeader("X-Request-Id", "evil\rX-Test: 1");

    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader("X-Request-Id")).startsWith("req_");
  }

  @Test
  void rejectsOversizedClientRequestId() throws Exception {
    ObservabilityProperties properties = new ObservabilityProperties();
    RequestIdFilter filter = new RequestIdFilter(properties);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
    request.addHeader("X-Request-Id", "a".repeat(129));

    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader("X-Request-Id")).startsWith("req_");
  }

  @Test
  void rejectsClientRequestIdContainingSpaces() throws Exception {
    ObservabilityProperties properties = new ObservabilityProperties();
    RequestIdFilter filter = new RequestIdFilter(properties);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
    request.addHeader("X-Request-Id", "with spaces");

    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(response.getHeader("X-Request-Id")).startsWith("req_");
  }

  @Test
  void acceptsValidClientRequestId() throws Exception {
    ObservabilityProperties properties = new ObservabilityProperties();
    RequestIdFilter filter = new RequestIdFilter(properties);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
    request.addHeader("X-Request-Id", "trace-abc_123:42");

    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertEquals("trace-abc_123:42", response.getHeader("X-Request-Id"));
  }

  @Test
  void traceIdFallsBackToRequestIdWhenClientValueInvalid() throws Exception {
    ObservabilityProperties properties = new ObservabilityProperties();
    RequestIdFilter filter = new RequestIdFilter(properties);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
    request.addHeader("X-Request-Id", "req_safe");
    request.addHeader("X-Trace-Id", "trace with space");

    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertEquals("req_safe", response.getHeader("X-Trace-Id"));
  }

  @Test
  void traceIdFallsBackToRequestIdWhenClientValueMissing() throws Exception {
    ObservabilityProperties properties = new ObservabilityProperties();
    RequestIdFilter filter = new RequestIdFilter(properties);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
    request.addHeader("X-Request-Id", "req_safe");

    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertEquals("req_safe", response.getHeader("X-Trace-Id"));
  }
}

package io.aegisops.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.aegisops.common.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TenantMdcFilterTest {
  @Test
  void syncsTenantContextToMdcAndClearsIt() throws Exception {
    ObservabilityProperties properties = new ObservabilityProperties();
    TenantMdcFilter filter = new TenantMdcFilter(properties);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/incidents/inc_1");
    MockHttpServletResponse response = new MockHttpServletResponse();

    TenantContext.setTenantId("tenant_1");
    try {
      filter.doFilter(
          request,
          response,
          (req, res) ->
              assertEquals("tenant_1", MDC.get(ObservabilityConstants.MDC_TENANT_ID)));
    } finally {
      TenantContext.clear();
    }

    assertNull(MDC.get(ObservabilityConstants.MDC_TENANT_ID));
  }

  @Test
  void fallsBackToTenantHeader() throws Exception {
    ObservabilityProperties properties = new ObservabilityProperties();
    TenantMdcFilter filter = new TenantMdcFilter(properties);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/agent/test");
    request.addHeader("X-Tenant-Id", "tenant_header");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (req, res) ->
            assertEquals("tenant_header", MDC.get(ObservabilityConstants.MDC_TENANT_ID)));

    assertNull(MDC.get(ObservabilityConstants.MDC_TENANT_ID));
  }
}

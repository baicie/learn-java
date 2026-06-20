package io.aegisops.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TenantRequiredFilterTest {
  @Test
  void rejectsApiRequestWithoutTenant() throws Exception {
    var props = new AiopsSecurityProperties();
    props.setTenantRequired(true);

    var filter =
        new TenantRequiredFilter(
            props,
            new SecurityErrorResponseWriter(new ObjectMapper()),
            new TenantSecurityAuditService(
                command -> {},
                new ObjectMapper()));

    var request = new MockHttpServletRequest("GET", "/api/incidents/inc_1");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertEquals(400, response.getStatus());
    assertTrue(response.getContentAsString().contains("TENANT_REQUIRED"));
  }

  @Test
  void allowsApiRequestWithTenantHeader() throws Exception {
    var props = new AiopsSecurityProperties();
    props.setTenantRequired(true);

    var filter =
        new TenantRequiredFilter(
            props,
            new SecurityErrorResponseWriter(new ObjectMapper()),
            new TenantSecurityAuditService(
                command -> {},
                new ObjectMapper()));

    var request = new MockHttpServletRequest("GET", "/api/incidents/inc_1");
    request.addHeader(SecurityConstants.HEADER_TENANT_ID, "tenant_1");
    var response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request, response, chain);

    assertEquals(200, response.getStatus());
    assertTrue(chain.called);
  }

  private static class RecordingChain implements FilterChain {
    boolean called;

    @Override
    public void doFilter(
        jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
      called = true;
    }
  }
}

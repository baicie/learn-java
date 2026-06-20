package io.aegisops.security;

import io.aegisops.common.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class TenantRequiredFilterTest {
  @Test
  void rejectsApiRequestWithoutTenant() throws Exception {
    var props = new AiopsSecurityProperties();
    props.setTenantRequired(true);

    var filter =
        new TenantRequiredFilter(
            props,
            new SecurityErrorResponseWriter(new ObjectMapper()),
            fakeAuditService());

    var request = new MockHttpServletRequest("GET", "/api/incidents/inc_1");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    org.junit.jupiter.api.Assertions.assertEquals(400, response.getStatus());
    org.junit.jupiter.api.Assertions.assertTrue(
        response.getContentAsString().contains("TENANT_REQUIRED"));
  }

  @Test
  void allowsApiRequestWithTenantHeader() throws Exception {
    var props = new AiopsSecurityProperties();
    props.setTenantRequired(true);

    var filter =
        new TenantRequiredFilter(
            props,
            new SecurityErrorResponseWriter(new ObjectMapper()),
            fakeAuditService());

    var request = new MockHttpServletRequest("GET", "/api/incidents/inc_1");
    request.addHeader(SecurityConstants.HEADER_TENANT_ID, "tenant_1");
    var response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request, response, chain);

    org.junit.jupiter.api.Assertions.assertEquals(200, response.getStatus());
    org.junit.jupiter.api.Assertions.assertTrue(chain.called);
  }

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    TenantContext.clear();
  }

  @Test
  void allowsApiRequestWithAuthenticatedPrincipalTenant() throws Exception {
    var props = new AiopsSecurityProperties();
    props.setTenantRequired(true);

    var filter =
        new TenantRequiredFilter(
            props,
            new SecurityErrorResponseWriter(new ObjectMapper()),
            fakeAuditService());

    var request = new MockHttpServletRequest("GET", "/api/incidents/inc_1");
    var response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    UserPrincipal principal =
        new UserPrincipal("user_1", "tenant_1", "alice", "Alice", Set.of("admin"));

    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

    try {
      filter.doFilter(request, response, chain);
    } finally {
      SecurityContextHolder.clearContext();
      TenantContext.clear();
    }

    org.junit.jupiter.api.Assertions.assertEquals(200, response.getStatus());
    org.junit.jupiter.api.Assertions.assertTrue(chain.called);
  }

  private static TenantSecurityAuditService fakeAuditService() {
    return new TenantSecurityAuditService(
        new TenantSecurityEventRepository() {
          @Override
          public void create(TenantSecurityEventCreateCommand command) {}
        },
        new ObjectMapper());
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

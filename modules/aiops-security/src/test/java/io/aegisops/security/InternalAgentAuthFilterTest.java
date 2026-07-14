package io.aegisops.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalAgentAuthFilterTest {
  @Test
  void rejectsMissingTokenForInternalAgentApi() throws Exception {
    var props = new AiopsSecurityProperties();
    props.setInternalAgentToken("secret");

    var audit = new FakeAuditService();
    var filter =
        new InternalAgentAuthFilter(
            props,
            new SecurityErrorResponseWriter(new ObjectMapper().findAndRegisterModules()),
            audit);

    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/memories/search");
    request.addHeader(SecurityConstants.HEADER_TENANT_ID, "tenant_1");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new NoopChain());

    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("INTERNAL_AGENT_AUTH_FAILED"));
    assertEquals(1, audit.events.size());
  }

  @Test
  void allowsValidTokenForInternalAgentApi() throws Exception {
    var props = new AiopsSecurityProperties();
    props.setInternalAgentToken("secret");

    var filter =
        new InternalAgentAuthFilter(
            props,
            new SecurityErrorResponseWriter(new ObjectMapper().findAndRegisterModules()),
            new FakeAuditService());

    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/memories/search");
    request.addHeader(SecurityConstants.HEADER_TENANT_ID, "tenant_1");
    request.addHeader(SecurityConstants.HEADER_INTERNAL_AGENT_TOKEN, "secret");
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request, response, chain);

    assertEquals(200, response.getStatus());
    assertTrue(chain.called);
  }

  private static class NoopChain implements FilterChain {
    @Override
    public void doFilter(
        jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {}
  }

  private static class RecordingChain implements FilterChain {
    boolean called;

    @Override
    public void doFilter(
        jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
        throws IOException, ServletException {
      called = true;
    }
  }

  private static class FakeAuditService extends TenantSecurityAuditService {
    final List<String> events = new ArrayList<>();

    FakeAuditService() {
      super(
          new TenantSecurityEventRepository() {
            @Override
            public void create(TenantSecurityEventCreateCommand command) {}
          },
          new ObjectMapper());
    }

    @Override
    public void record(
        String tenantId,
        String eventType,
        String severity,
        String summary,
        jakarta.servlet.http.HttpServletRequest request) {
      events.add(eventType);
    }
  }
}

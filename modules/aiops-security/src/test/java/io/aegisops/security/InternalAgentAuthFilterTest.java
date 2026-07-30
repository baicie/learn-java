package io.aegisops.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.security.DiagnosisGrantClaims;
import io.aegisops.common.security.DiagnosisGrantCodec;
import io.aegisops.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalAgentAuthFilterTest {
  private static final String GRANT_SECRET = "test-diagnosis-grant-secret-with-32-bytes";
  private static final Instant NOW = Instant.parse("2026-07-30T08:00:00Z");

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
  void rejectsValidServiceTokenWithoutDiagnosisGrant() throws Exception {
    var props = new AiopsSecurityProperties();
    props.setInternalAgentToken("secret");
    props.setDiagnosisGrantSecret(GRANT_SECRET);

    var filter =
        new InternalAgentAuthFilter(
            props,
            new SecurityErrorResponseWriter(new ObjectMapper().findAndRegisterModules()),
            new FakeAuditService(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/memories/search");
    request.addHeader(SecurityConstants.HEADER_TENANT_ID, "tenant_1");
    request.addHeader(SecurityConstants.HEADER_INTERNAL_AGENT_TOKEN, "secret");
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request, response, chain);

    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("DIAGNOSIS_GRANT_INVALID"));
    assertTrue(!chain.called);
  }

  @Test
  void derivesTenantFromDiagnosisGrantInsteadOfUntrustedHeader() throws Exception {
    var props = new AiopsSecurityProperties();
    props.setInternalAgentToken("secret");
    props.setDiagnosisGrantSecret(GRANT_SECRET);

    var filter =
        new InternalAgentAuthFilter(
            props,
            new SecurityErrorResponseWriter(new ObjectMapper().findAndRegisterModules()),
            new FakeAuditService(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/memories/search");
    request.addHeader(SecurityConstants.HEADER_TENANT_ID, "forged_tenant");
    request.addHeader(SecurityConstants.HEADER_INTERNAL_AGENT_TOKEN, "secret");
    request.addHeader(SecurityConstants.HEADER_DIAGNOSIS_GRANT, grant("tenant_1"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request, response, chain);

    assertEquals(200, response.getStatus());
    assertTrue(chain.called);
    assertEquals("tenant_1", chain.tenantId);
    assertEquals(null, TenantContext.getTenantId());
  }

  private String grant(String tenantId) {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    return new DiagnosisGrantCodec(new ObjectMapper(), clock)
        .issue(
            GRANT_SECRET,
            new DiagnosisGrantClaims(
                "aiops-server",
                "aegisops-internal-api",
                tenantId,
                "inc_1",
                "trace_1",
                NOW,
                NOW.plusSeconds(300)));
  }

  private static class NoopChain implements FilterChain {
    @Override
    public void doFilter(
        jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {}
  }

  private static class RecordingChain implements FilterChain {
    boolean called;
    String tenantId;

    @Override
    public void doFilter(
        jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
        throws IOException, ServletException {
      called = true;
      tenantId = TenantContext.getTenantId();
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

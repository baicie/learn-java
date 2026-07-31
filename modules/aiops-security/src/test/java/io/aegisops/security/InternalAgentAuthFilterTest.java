package io.aegisops.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.security.DiagnosisGrantAuthorization;
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
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;

class InternalAgentAuthFilterTest {
  private static final String GRANT_SECRET = "test-diagnosis-grant-secret-with-32-bytes";
  private static final Instant NOW = Instant.parse("2026-07-30T08:00:00Z");

  @Test
  void defaultGrantIssuerAllowlistContainsOnlyDeployedJavaCallers() {
    assertEquals(
        List.of("aiops-server", "aiops-worker"),
        new AiopsSecurityProperties().getDiagnosisGrantIssuers());
  }

  @Test
  void filtersInternalAgentRootPath() {
    var filter =
        new InternalAgentAuthFilter(
            new AiopsSecurityProperties(),
            new SecurityErrorResponseWriter(new ObjectMapper().findAndRegisterModules()),
            new FakeAuditService(),
            request -> new InternalServicePrincipal("svc:aiops-agent", Set.of()),
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertFalse(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/internal/agent")));
  }

  @Test
  void rejectsInvalidGrantConfigurationWhenFilterIsConstructedDirectly() {
    var props = new AiopsSecurityProperties();
    props.setDiagnosisGrantSecret(" ".repeat(32));

    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class,
        () ->
            new InternalAgentAuthFilter(
                props,
                new SecurityErrorResponseWriter(new ObjectMapper().findAndRegisterModules()),
                new FakeAuditService(),
                request -> new InternalServicePrincipal("svc:aiops-agent", Set.of("memory:read")),
                Clock.fixed(NOW, ZoneOffset.UTC)));
  }

  @Test
  void rejectsMissingTokenForInternalAgentApi() throws Exception {
    var props = new AiopsSecurityProperties();

    var audit = new FakeAuditService();
    var filter =
        new InternalAgentAuthFilter(
            props,
            new SecurityErrorResponseWriter(new ObjectMapper().findAndRegisterModules()),
            audit,
            request -> {
              throw new InternalServiceAuthenticationException("Bearer service token is required");
            },
            Clock.fixed(NOW, ZoneOffset.UTC));

    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/memories/search");
    request.addHeader(SecurityConstants.HEADER_TENANT_ID, "tenant_1");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new NoopChain());

    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("INTERNAL_AGENT_AUTH_FAILED"));
    assertEquals(1, audit.events.size());
    assertEquals(null, audit.tenantIds.get(0));
  }

  @Test
  void rejectsValidServiceTokenWithoutEndpointScopeAsForbidden() throws Exception {
    var props = new AiopsSecurityProperties();
    var audit = new FakeAuditService();
    var authenticator =
        new JwtInternalServiceAuthenticator(
            props,
            token ->
                new Jwt(
                    token,
                    NOW,
                    NOW.plusSeconds(300),
                    Map.of("alg", "RS256"),
                    Map.of(
                        "sub",
                        "svc:aiops-agent",
                        "aud",
                        List.of("aegisops-internal-api"),
                        "scope",
                        "cases:read")));
    var filter =
        new InternalAgentAuthFilter(
            props,
            new SecurityErrorResponseWriter(new ObjectMapper().findAndRegisterModules()),
            audit,
            authenticator,
            Clock.fixed(NOW, ZoneOffset.UTC));

    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/evidence/query");
    request.addHeader("Authorization", "Bearer service-token");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new NoopChain());

    assertEquals(403, response.getStatus());
    assertTrue(response.getContentAsString().contains("INTERNAL_AGENT_AUTH_FORBIDDEN"));
    assertTrue(!response.getContentAsString().contains("evidence:read"));
    assertEquals(List.of("internal_auth_forbidden"), audit.events);
  }

  @Test
  void rejectsValidServiceTokenWithoutDiagnosisGrant() throws Exception {
    var props = new AiopsSecurityProperties();
    props.setDiagnosisGrantSecret(GRANT_SECRET);

    var audit = new FakeAuditService();
    var filter =
        new InternalAgentAuthFilter(
            props,
            new SecurityErrorResponseWriter(new ObjectMapper().findAndRegisterModules()),
            audit,
            request -> new InternalServicePrincipal("svc:aiops-agent", Set.of("memory:read")),
            Clock.fixed(NOW, ZoneOffset.UTC));

    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/memories/search");
    request.addHeader(SecurityConstants.HEADER_TENANT_ID, "forged_tenant");
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request, response, chain);

    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("DIAGNOSIS_GRANT_INVALID"));
    assertTrue(!chain.called);
    assertEquals(List.of("diagnosis_grant_invalid"), audit.events);
    assertEquals(null, audit.tenantIds.get(0));
  }

  @Test
  void configurationCannotDisableDiagnosisGrant() throws Exception {
    var props =
        new Binder(
                new MapConfigurationPropertySource(
                    Map.of(
                        "aiops.security.diagnosis-grant-secret",
                        GRANT_SECRET,
                        "aiops.security.diagnosis-grant-required",
                        "false")))
            .bind("aiops.security", Bindable.of(AiopsSecurityProperties.class))
            .get();
    var audit = new FakeAuditService();
    var filter =
        new InternalAgentAuthFilter(
            props,
            new SecurityErrorResponseWriter(new ObjectMapper().findAndRegisterModules()),
            audit,
            request -> new InternalServicePrincipal("svc:aiops-agent", Set.of("memory:read")),
            Clock.fixed(NOW, ZoneOffset.UTC));
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/memories/search");
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request, response, chain);

    assertEquals(401, response.getStatus());
    assertTrue(!chain.called);
    assertEquals(List.of("diagnosis_grant_invalid"), audit.events);
  }

  @Test
  void reportsIdentityProviderFailureAsServiceUnavailable() throws Exception {
    var props = new AiopsSecurityProperties();
    var audit = new FakeAuditService();
    var filter =
        new InternalAgentAuthFilter(
            props,
            new SecurityErrorResponseWriter(new ObjectMapper().findAndRegisterModules()),
            audit,
            request -> {
              throw new InternalServiceAuthenticationUnavailableException(
                  "Service authentication is temporarily unavailable");
            },
            Clock.fixed(NOW, ZoneOffset.UTC));

    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/evidence/query");
    request.addHeader("Authorization", "Bearer service-token");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new NoopChain());

    assertEquals(503, response.getStatus());
    assertTrue(response.getContentAsString().contains("INTERNAL_AGENT_AUTH_UNAVAILABLE"));
    assertEquals(List.of("internal_auth_unavailable"), audit.events);
    assertEquals(1, audit.tenantIds.size());
    assertEquals(null, audit.tenantIds.get(0));
  }

  @Test
  void derivesTenantFromDiagnosisGrantInsteadOfUntrustedHeader() throws Exception {
    var props = new AiopsSecurityProperties();
    props.setDiagnosisGrantSecret(GRANT_SECRET);

    var filter =
        new InternalAgentAuthFilter(
            props,
            new SecurityErrorResponseWriter(new ObjectMapper().findAndRegisterModules()),
            new FakeAuditService(),
            request -> new InternalServicePrincipal("svc:aiops-agent", Set.of("memory:read")),
            Clock.fixed(NOW, ZoneOffset.UTC));

    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/memories/search");
    request.addHeader(SecurityConstants.HEADER_TENANT_ID, "forged_tenant");
    request.addHeader(SecurityConstants.HEADER_DIAGNOSIS_GRANT, grant("tenant_1"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request, response, chain);

    assertEquals(200, response.getStatus());
    assertTrue(chain.called);
    assertEquals("tenant_1", chain.tenantId);
    assertEquals(null, TenantContext.getTenantId());
  }

  @Test
  void auditsForbiddenGrantContextAgainstVerifiedTenant() throws Exception {
    var props = new AiopsSecurityProperties();
    props.setDiagnosisGrantSecret(GRANT_SECRET);

    var audit = new FakeAuditService();
    var filter =
        new InternalAgentAuthFilter(
            props,
            new SecurityErrorResponseWriter(new ObjectMapper().findAndRegisterModules()),
            audit,
            request -> new InternalServicePrincipal("svc:aiops-agent", Set.of("evidence:read")),
            Clock.fixed(NOW, ZoneOffset.UTC));

    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/evidence/query");
    request.addHeader(SecurityConstants.HEADER_TENANT_ID, "forged_tenant");
    request.addHeader(SecurityConstants.HEADER_DIAGNOSIS_GRANT, grant("tenant_1"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new GrantContextMismatchChain());

    assertEquals(403, response.getStatus());
    assertEquals(List.of("internal_auth_forbidden"), audit.events);
    assertEquals(List.of("tenant_1"), audit.tenantIds);
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

  private static class GrantContextMismatchChain implements FilterChain {
    @Override
    public void doFilter(
        jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
      DiagnosisGrantClaims claims =
          (DiagnosisGrantClaims)
              request.getAttribute(SecurityConstants.REQUEST_ATTRIBUTE_DIAGNOSIS_GRANT);
      try {
        DiagnosisGrantAuthorization.requireContext(
            claims, "tenant_1", "different_incident", "trace_1");
      } catch (SecurityException exception) {
        ((jakarta.servlet.http.HttpServletResponse) response).setStatus(403);
      }
    }
  }

  private static class FakeAuditService extends TenantSecurityAuditService {
    final List<String> events = new ArrayList<>();
    final List<String> tenantIds = new ArrayList<>();

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
      tenantIds.add(tenantId);
    }
  }
}

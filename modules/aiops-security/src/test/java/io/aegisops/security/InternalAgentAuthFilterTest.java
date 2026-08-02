package io.aegisops.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.security.DiagnosisGrantAuthorization;
import io.aegisops.common.security.DiagnosisGrantClaims;
import io.aegisops.common.security.DiagnosisGrantCodec;
import io.aegisops.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalAgentAuthFilterTest {
  private static final String KEY_ID = "task-grant-v1";
  private static final Instant NOW = Instant.parse("2026-07-30T08:00:00Z");
  private static KeyPair signingKeys;

  @BeforeAll
  static void generateSigningKeys() throws Exception {
    signingKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
  }

  @Test
  void defaultGrantIssuerAllowlistContainsOnlyControlPlane() {
    assertEquals(List.of("aegisops-app"), new AiopsSecurityProperties().getDiagnosisGrantIssuers());
  }

  @Test
  void filtersInternalAgentRootPath() {
    InternalAgentAuthFilter filter = filter(successfulAuthenticator(), signingKeys);

    assertFalse(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/internal/agent")));
  }

  @Test
  void rejectsMissingTlsClientIdentity() throws Exception {
    FakeAuditService audit = new FakeAuditService();
    InternalAgentAuthFilter filter =
        filter(
            request -> {
              throw new InternalServiceAuthenticationException(
                  "TLS client certificate is required");
            },
            signingKeys,
            audit);
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/memories/search");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new NoopChain());

    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("INTERNAL_AGENT_AUTH_FAILED"));
    assertEquals(List.of("internal_auth_failed"), audit.events);
  }

  @Test
  void rejectsValidMtlsIdentityWithoutDiagnosisGrant() throws Exception {
    FakeAuditService audit = new FakeAuditService();
    InternalAgentAuthFilter filter = filter(successfulAuthenticator(), signingKeys, audit);
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/memories/search");
    request.addHeader(SecurityConstants.HEADER_TENANT_ID, "forged_tenant");
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request, response, chain);

    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("DIAGNOSIS_GRANT_INVALID"));
    assertFalse(chain.called);
    assertEquals(List.of("diagnosis_grant_invalid"), audit.events);
    assertNull(audit.tenantIds.getFirst());
  }

  @Test
  void rejectsGrantSignedByUnknownKey() throws Exception {
    KeyPair unknownKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    InternalAgentAuthFilter filter = filter(successfulAuthenticator(), signingKeys);
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/memories/search");
    request.addHeader(
        SecurityConstants.HEADER_DIAGNOSIS_GRANT,
        grant(unknownKeys, "tenant_1", List.of("memory:read")));
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new NoopChain());

    assertEquals(401, response.getStatus());
    assertTrue(response.getContentAsString().contains("DIAGNOSIS_GRANT_INVALID"));
  }

  @Test
  void rejectsGrantWithoutEndpointScopeAsForbidden() throws Exception {
    FakeAuditService audit = new FakeAuditService();
    InternalAgentAuthFilter filter = filter(successfulAuthenticator(), signingKeys, audit);
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/evidence/query");
    request.addHeader(
        SecurityConstants.HEADER_DIAGNOSIS_GRANT,
        grant(signingKeys, "tenant_1", List.of("cases:read")));
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request, response, chain);

    assertEquals(403, response.getStatus());
    assertTrue(response.getContentAsString().contains("INTERNAL_AGENT_AUTH_FORBIDDEN"));
    assertFalse(response.getContentAsString().contains("evidence:read"));
    assertFalse(chain.called);
    assertEquals(List.of("internal_auth_forbidden"), audit.events);
    assertEquals(List.of("tenant_1"), audit.tenantIds);
  }

  @Test
  void derivesTenantFromDiagnosisGrantInsteadOfUntrustedHeader() throws Exception {
    InternalAgentAuthFilter filter = filter(successfulAuthenticator(), signingKeys);
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/memories/search");
    request.addHeader(SecurityConstants.HEADER_TENANT_ID, "forged_tenant");
    request.addHeader(
        SecurityConstants.HEADER_DIAGNOSIS_GRANT,
        grant(signingKeys, "tenant_1", List.of("memory:read")));
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request, response, chain);

    assertEquals(200, response.getStatus());
    assertTrue(chain.called);
    assertEquals("tenant_1", chain.tenantId);
    assertNull(TenantContext.getTenantId());
  }

  @Test
  void auditsForbiddenGrantContextAgainstVerifiedTenant() throws Exception {
    FakeAuditService audit = new FakeAuditService();
    InternalAgentAuthFilter filter = filter(successfulAuthenticator(), signingKeys, audit);
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/evidence/query");
    request.addHeader(SecurityConstants.HEADER_TENANT_ID, "forged_tenant");
    request.addHeader(
        SecurityConstants.HEADER_DIAGNOSIS_GRANT,
        grant(signingKeys, "tenant_1", List.of("evidence:read")));
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new GrantContextMismatchChain());

    assertEquals(403, response.getStatus());
    assertEquals(List.of("internal_auth_forbidden"), audit.events);
    assertEquals(List.of("tenant_1"), audit.tenantIds);
  }

  private InternalAgentAuthFilter filter(
      InternalServiceAuthenticator authenticator, KeyPair verificationKeyPair) {
    return filter(authenticator, verificationKeyPair, new FakeAuditService());
  }

  private InternalAgentAuthFilter filter(
      InternalServiceAuthenticator authenticator,
      KeyPair verificationKeyPair,
      FakeAuditService audit) {
    AiopsSecurityProperties properties = new AiopsSecurityProperties();
    return new InternalAgentAuthFilter(
        properties,
        new SecurityErrorResponseWriter(new ObjectMapper().findAndRegisterModules()),
        audit,
        authenticator,
        new DiagnosisGrantVerificationKeys(Map.of(KEY_ID, verificationKeyPair.getPublic())),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private InternalServiceAuthenticator successfulAuthenticator() {
    return request ->
        new InternalServicePrincipal("spiffe://aegisops.local/service/aiops-agent", Set.of());
  }

  private String grant(KeyPair keys, String tenantId, List<String> scopes) {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    return new DiagnosisGrantCodec(new ObjectMapper(), clock)
        .issue(
            keys.getPrivate(),
            KEY_ID,
            new DiagnosisGrantClaims(
                "aegisops-app",
                "diagnosis:diag_1",
                Set.of("aegisops-internal-api", "aiops-agent-api"),
                scopes,
                tenantId,
                "inc_1",
                "diag_1",
                "trace_1",
                NOW,
                NOW.plusSeconds(300),
                "grant_1"));
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
      super(command -> {}, new ObjectMapper());
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

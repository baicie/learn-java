package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.RemoteKeySourceException;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtInternalServiceAuthenticatorTest {
  @Test
  void authenticatesExpectedAudienceAndEndpointScope() {
    AiopsSecurityProperties properties = oauthProperties();
    JwtDecoder decoder =
        token ->
            jwt(
                Map.of(
                    "sub",
                    "svc:aiops-agent",
                    "aud",
                    List.of("aegisops-internal-api"),
                    "scope",
                    "evidence:read cases:read"));
    JwtInternalServiceAuthenticator authenticator =
        new JwtInternalServiceAuthenticator(properties, decoder);
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/evidence/query");
    request.addHeader("Authorization", "Bearer service-token");

    InternalServicePrincipal principal = authenticator.authenticate(request);

    assertThat(principal.serviceId()).isEqualTo("svc:aiops-agent");
    assertThat(principal.scopes()).contains("evidence:read");
  }

  @Test
  void acceptsCaseInsensitiveBearerScheme() {
    AiopsSecurityProperties properties = oauthProperties();
    JwtDecoder decoder =
        token ->
            jwt(
                Map.of(
                    "sub",
                    "svc:aiops-agent",
                    "aud",
                    List.of("aegisops-internal-api"),
                    "scope",
                    "evidence:read"));
    JwtInternalServiceAuthenticator authenticator =
        new JwtInternalServiceAuthenticator(properties, decoder);

    for (String scheme : List.of("bearer", "BEARER")) {
      MockHttpServletRequest request =
          new MockHttpServletRequest("POST", "/internal/agent/evidence/query");
      request.addHeader("Authorization", scheme + " service-token");

      assertThat(authenticator.authenticate(request).serviceId()).isEqualTo("svc:aiops-agent");
    }
  }

  @Test
  void rejectsMalformedAuthorizationFormat() {
    AiopsSecurityProperties properties = oauthProperties();
    JwtInternalServiceAuthenticator authenticator =
        new JwtInternalServiceAuthenticator(properties, token -> jwt(Map.of()));

    for (String authorization :
        List.of("Basic service-token", "Bearer", "Bearer ", "Bearer\tservice-token")) {
      MockHttpServletRequest request =
          new MockHttpServletRequest("POST", "/internal/agent/evidence/query");
      request.addHeader("Authorization", authorization);

      assertThatThrownBy(() -> authenticator.authenticate(request))
          .isInstanceOf(InternalServiceAuthenticationException.class)
          .hasMessageContaining("Bearer service token is required");
    }
  }

  @Test
  void rejectsWrongAudienceOrMissingScope() {
    AiopsSecurityProperties properties = oauthProperties();
    JwtDecoder wrongAudience =
        token ->
            jwt(
                Map.of(
                    "sub",
                    "svc:aiops-agent",
                    "aud",
                    List.of("different-api"),
                    "scope",
                    "evidence:read"));
    JwtDecoder missingScope =
        token ->
            jwt(
                Map.of(
                    "sub",
                    "svc:aiops-agent",
                    "aud",
                    List.of("aegisops-internal-api"),
                    "scope",
                    "cases:read"));
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/evidence/query");
    request.addHeader("Authorization", "Bearer service-token");

    assertThatThrownBy(
            () ->
                new JwtInternalServiceAuthenticator(properties, wrongAudience)
                    .authenticate(request))
        .isInstanceOf(InternalServiceAuthenticationException.class)
        .hasMessageContaining("audience");
    assertThatThrownBy(
            () ->
                new JwtInternalServiceAuthenticator(properties, missingScope).authenticate(request))
        .isInstanceOf(InternalServiceAuthenticationException.class)
        .hasMessageContaining("scope");
  }

  @Test
  void rejectsTokenWithoutAudienceAsAuthenticationFailure() {
    AiopsSecurityProperties properties = oauthProperties();
    JwtDecoder missingAudience =
        token -> jwt(Map.of("sub", "svc:aiops-agent", "scope", "evidence:read"));
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/evidence/query");
    request.addHeader("Authorization", "Bearer service-token");

    assertThatThrownBy(
            () ->
                new JwtInternalServiceAuthenticator(properties, missingAudience)
                    .authenticate(request))
        .isInstanceOf(InternalServiceAuthenticationException.class)
        .hasMessageContaining("audience");
  }

  @Test
  void rejectsTokenWithoutExpiration() {
    AiopsSecurityProperties properties = oauthProperties();
    JwtDecoder missingExpiration =
        token ->
            jwtWithoutExpiration(
                Map.of(
                    "sub",
                    "svc:aiops-agent",
                    "aud",
                    List.of("aegisops-internal-api"),
                    "scope",
                    "evidence:read"));
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/evidence/query");
    request.addHeader("Authorization", "Bearer service-token");

    assertThatThrownBy(
            () ->
                new JwtInternalServiceAuthenticator(properties, missingExpiration)
                    .authenticate(request))
        .isInstanceOf(InternalServiceAuthenticationException.class)
        .hasMessageContaining("expiration");
  }

  @Test
  void rejectsTokenWithoutIssuedAt() {
    AiopsSecurityProperties properties = oauthProperties();
    JwtDecoder missingIssuedAt =
        token ->
            jwtWithoutIssuedAt(
                Map.of(
                    "sub",
                    "svc:aiops-agent",
                    "aud",
                    List.of("aegisops-internal-api"),
                    "scope",
                    "evidence:read"));
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/evidence/query");
    request.addHeader("Authorization", "Bearer service-token");

    assertThatThrownBy(
            () ->
                new JwtInternalServiceAuthenticator(properties, missingIssuedAt)
                    .authenticate(request))
        .isInstanceOf(InternalServiceAuthenticationException.class)
        .hasMessageContaining("issued-at");
  }

  @Test
  void rejectsTokenLifetimeOverFiveMinutes() {
    AiopsSecurityProperties properties = oauthProperties();
    JwtDecoder longLived =
        token ->
            jwtWithLifetime(
                Map.of(
                    "sub",
                    "svc:aiops-agent",
                    "aud",
                    List.of("aegisops-internal-api"),
                    "scope",
                    "evidence:read"),
                301);
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/evidence/query");
    request.addHeader("Authorization", "Bearer service-token");

    assertThatThrownBy(
            () -> new JwtInternalServiceAuthenticator(properties, longLived).authenticate(request))
        .isInstanceOf(InternalServiceAuthenticationException.class)
        .hasMessageContaining("lifetime");
  }

  @Test
  void rejectsTokenIssuedTooFarInFuture() {
    AiopsSecurityProperties properties = oauthProperties();
    Instant issuedAt = Instant.parse("2100-01-01T00:00:00Z");
    JwtDecoder futureToken =
        token ->
            jwtWithTimes(
                Map.of(
                    "sub",
                    "svc:aiops-agent",
                    "aud",
                    List.of("aegisops-internal-api"),
                    "scope",
                    "evidence:read"),
                issuedAt,
                issuedAt.plusSeconds(300));
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/evidence/query");
    request.addHeader("Authorization", "Bearer service-token");

    assertThatThrownBy(
            () ->
                new JwtInternalServiceAuthenticator(properties, futureToken).authenticate(request))
        .isInstanceOf(InternalServiceAuthenticationException.class)
        .hasMessageContaining("issued-at");
  }

  @Test
  void reportsRemoteJwksFailureAsAuthenticationUnavailable() {
    AiopsSecurityProperties properties = oauthProperties();
    JwtDecoder unavailable =
        token -> {
          throw new JwtException(
              "Unable to retrieve remote JWK set",
              new RemoteKeySourceException(
                  "JWKS unavailable", new IOException("connection refused")));
        };
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/evidence/query");
    request.addHeader("Authorization", "Bearer service-token");

    assertThatThrownBy(
            () ->
                new JwtInternalServiceAuthenticator(properties, unavailable).authenticate(request))
        .isInstanceOf(InternalServiceAuthenticationUnavailableException.class)
        .hasMessageContaining("temporarily unavailable");
  }

  @Test
  void authorizesCheckpointCollectionWriteWithCheckpointWriteScope() {
    AiopsSecurityProperties properties = oauthProperties();
    JwtDecoder decoder =
        token ->
            jwt(
                Map.of(
                    "sub",
                    "svc:aiops-agent",
                    "aud",
                    List.of("aegisops-internal-api"),
                    "scope",
                    "checkpoint:write"));
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/checkpoints");
    request.addHeader("Authorization", "Bearer service-token");

    InternalServicePrincipal principal =
        new JwtInternalServiceAuthenticator(properties, decoder).authenticate(request);

    assertThat(principal.scopes()).contains("checkpoint:write");
  }

  @Test
  void authorizesReadOnlyAuthProbeWithEvidenceReadScope() {
    AiopsSecurityProperties properties = oauthProperties();
    JwtDecoder decoder =
        token ->
            jwt(
                Map.of(
                    "sub",
                    "svc:aiops-agent",
                    "aud",
                    List.of("aegisops-internal-api"),
                    "scope",
                    "evidence:read"));
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/auth/probe");
    request.addHeader("Authorization", "Bearer service-token");

    JwtInternalServiceAuthenticator authenticator =
        new JwtInternalServiceAuthenticator(properties, decoder);
    assertThatCode(() -> authenticator.authenticate(request)).doesNotThrowAnyException();
    InternalServicePrincipal principal =
        (InternalServicePrincipal)
            request.getAttribute(SecurityConstants.REQUEST_ATTRIBUTE_SERVICE_PRINCIPAL);

    assertThat(principal.serviceId()).isEqualTo("svc:aiops-agent");
  }

  @Test
  void rejectsInternalAgentEndpointWithoutExplicitScopePolicy() {
    AiopsSecurityProperties properties = oauthProperties();
    JwtDecoder decoder =
        token ->
            jwt(
                Map.of(
                    "sub",
                    "svc:aiops-agent",
                    "aud",
                    List.of("aegisops-internal-api"),
                    "scope",
                    "internal:agent"));
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/future-endpoint");
    request.addHeader("Authorization", "Bearer service-token");

    assertThatThrownBy(
            () -> new JwtInternalServiceAuthenticator(properties, decoder).authenticate(request))
        .isInstanceOf(InternalServiceAuthorizationException.class)
        .hasMessageContaining("scope policy");
  }

  @Test
  void exposesVerifiedServicePrincipalWhenEndpointScopeIsMissing() {
    AiopsSecurityProperties properties = oauthProperties();
    JwtDecoder decoder =
        token ->
            jwt(
                Map.of(
                    "sub",
                    "svc:aiops-agent",
                    "aud",
                    List.of("aegisops-internal-api"),
                    "scope",
                    "cases:read"));
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/evidence/query");
    request.addHeader("Authorization", "Bearer service-token");

    assertThatThrownBy(
            () -> new JwtInternalServiceAuthenticator(properties, decoder).authenticate(request))
        .isInstanceOf(InternalServiceAuthorizationException.class);
    assertThat(request.getAttribute(SecurityConstants.REQUEST_ATTRIBUTE_SERVICE_PRINCIPAL))
        .isEqualTo(new InternalServicePrincipal("svc:aiops-agent", java.util.Set.of("cases:read")));
  }

  private AiopsSecurityProperties oauthProperties() {
    AiopsSecurityProperties properties = new AiopsSecurityProperties();
    properties.setInternalAgentJwtAudience("aegisops-internal-api");
    return properties;
  }

  private Jwt jwt(Map<String, Object> claims) {
    return jwtWithLifetime(claims, 300);
  }

  private Jwt jwtWithLifetime(Map<String, Object> claims, long lifetimeSeconds) {
    Instant issuedAt = Instant.parse("2026-07-30T08:00:00Z");
    return jwtWithTimes(claims, issuedAt, issuedAt.plusSeconds(lifetimeSeconds));
  }

  private Jwt jwtWithTimes(Map<String, Object> claims, Instant issuedAt, Instant expiresAt) {
    return new Jwt("service-token", issuedAt, expiresAt, Map.of("alg", "RS256"), claims);
  }

  private Jwt jwtWithoutExpiration(Map<String, Object> claims) {
    Instant issuedAt = Instant.parse("2026-07-30T08:00:00Z");
    return new Jwt("service-token", issuedAt, null, Map.of("alg", "RS256"), claims);
  }

  private Jwt jwtWithoutIssuedAt(Map<String, Object> claims) {
    Instant expiresAt = Instant.parse("2026-07-30T08:05:00Z");
    return new Jwt("service-token", null, expiresAt, Map.of("alg", "RS256"), claims);
  }
}

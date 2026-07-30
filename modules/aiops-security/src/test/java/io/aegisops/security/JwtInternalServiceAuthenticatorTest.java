package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

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

  private AiopsSecurityProperties oauthProperties() {
    AiopsSecurityProperties properties = new AiopsSecurityProperties();
    properties.setInternalAgentAuthMode("oauth2");
    properties.setInternalAgentJwtAudience("aegisops-internal-api");
    return properties;
  }

  private Jwt jwt(Map<String, Object> claims) {
    Instant issuedAt = Instant.parse("2026-07-30T08:00:00Z");
    return new Jwt(
        "service-token", issuedAt, issuedAt.plusSeconds(300), Map.of("alg", "RS256"), claims);
  }
}

package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

class AiopsSecurityConfigurationTest {
  @Test
  void alwaysUsesJwtForInternalServiceAuthentication() {
    AiopsSecurityProperties properties = validProperties();

    InternalServiceAuthenticator authenticator =
        new AiopsSecurityConfiguration().internalServiceAuthenticator(properties);

    assertThat(authenticator).isInstanceOf(JwtInternalServiceAuthenticator.class);
  }

  @Test
  void rejectsWeakDiagnosisGrantSecretAtStartup() {
    AiopsSecurityProperties properties = validProperties();
    properties.setDiagnosisGrantSecret("x".repeat(31));

    assertThatThrownBy(
            () -> new AiopsSecurityConfiguration().internalServiceAuthenticator(properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("diagnosis-grant-secret");

    AiopsSecurityProperties blankSecret = validProperties();
    blankSecret.setDiagnosisGrantSecret(" ".repeat(32));
    assertThatThrownBy(
            () -> new AiopsSecurityConfiguration().internalServiceAuthenticator(blankSecret))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("diagnosis-grant-secret");
  }

  @Test
  void rejectsBlankIssuerAndAudienceConfigurationAtStartup() {
    AiopsSecurityProperties blankIssuer = validProperties();
    blankIssuer.setInternalAgentJwtIssuerUri(" ");
    assertThatThrownBy(
            () -> new AiopsSecurityConfiguration().internalServiceAuthenticator(blankIssuer))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("issuer-uri");

    AiopsSecurityProperties blankJwtAudience = validProperties();
    blankJwtAudience.setInternalAgentJwtAudience("");
    assertThatThrownBy(
            () -> new AiopsSecurityConfiguration().internalServiceAuthenticator(blankJwtAudience))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("jwt-audience");

    AiopsSecurityProperties blankGrantAudience = validProperties();
    blankGrantAudience.setDiagnosisGrantAudience(" ");
    assertThatThrownBy(
            () -> new AiopsSecurityConfiguration().internalServiceAuthenticator(blankGrantAudience))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("diagnosis-grant-audience");
  }

  @Test
  void exposesReadOnlyInternalServiceAuthenticationProbeController() {
    assertThatCode(
            () ->
                Class.forName(
                    "io.aegisops.security.InternalServiceAuthProbeController",
                    true,
                    getClass().getClassLoader()))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsServiceTokenImmediatelyAfterExpiration() {
    Instant now = Instant.parse("2026-08-01T08:00:00Z");
    JwtTimestampValidator validator =
        AiopsSecurityConfiguration.strictServiceTokenTimestampValidator();
    validator.setClock(Clock.fixed(now, ZoneOffset.UTC));
    Jwt expired =
        new Jwt(
            "service-token",
            now.minusSeconds(61),
            now.minusSeconds(1),
            Map.of("alg", "RS256"),
            Map.of("sub", "svc:aiops-agent"));

    assertThat(validator.validate(expired).hasErrors()).isTrue();
  }

  private static AiopsSecurityProperties validProperties() {
    AiopsSecurityProperties properties = new AiopsSecurityProperties();
    properties.setInternalAgentJwtIssuerUri("http://localhost:8089/realms/aegisops");
    properties.setInternalAgentJwtJwkSetUri(
        "http://localhost:8089/realms/aegisops/protocol/openid-connect/certs");
    return properties;
  }
}

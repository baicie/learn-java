package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class AiopsSecurityConfigurationTest {
  @Test
  void alwaysUsesMtlsForInternalServiceAuthentication() {
    AiopsSecurityProperties properties = new AiopsSecurityProperties();

    InternalServiceAuthenticator authenticator =
        new AiopsSecurityConfiguration().internalServiceAuthenticator(properties);

    assertThat(authenticator).isInstanceOf(MtlsInternalServiceAuthenticator.class);
  }

  @Test
  void rejectsBlankInternalAgentCertificateIdentityAtStartup() {
    AiopsSecurityProperties properties = new AiopsSecurityProperties();
    properties.setInternalAgentCertificateIdentities(List.of(" "));

    assertThatThrownBy(
            () -> new AiopsSecurityConfiguration().internalServiceAuthenticator(properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("certificate-identities");
  }

  @Test
  void validatesDiagnosisGrantAudienceAndIssuerAllowlist() {
    AiopsSecurityProperties blankAudience = new AiopsSecurityProperties();
    blankAudience.setDiagnosisGrantAudience(" ");
    assertThatThrownBy(blankAudience::validateDiagnosisGrant)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("diagnosis-grant-audience");

    AiopsSecurityProperties blankIssuer = new AiopsSecurityProperties();
    blankIssuer.setDiagnosisGrantIssuers(List.of(" "));
    assertThatThrownBy(blankIssuer::validateDiagnosisGrant)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("diagnosis-grant-issuers");
  }

  @Test
  void doesNotShipLegacyInternalJwtAuthenticator() {
    assertThatThrownBy(
            () ->
                Class.forName(
                    "io.aegisops.security.JwtInternalServiceAuthenticator",
                    true,
                    getClass().getClassLoader()))
        .isInstanceOf(ClassNotFoundException.class);
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
}

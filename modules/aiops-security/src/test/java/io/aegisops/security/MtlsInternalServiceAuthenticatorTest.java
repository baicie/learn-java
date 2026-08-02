package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.cert.X509Certificate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class MtlsInternalServiceAuthenticatorTest {
  private static final String AGENT_IDENTITY = "spiffe://aegisops.local/service/aiops-agent";

  @Test
  void authenticatesAgentUriSanFromTlsPeerCertificate() throws Exception {
    AiopsSecurityProperties properties = new AiopsSecurityProperties();
    properties.setInternalAgentCertificateIdentities(List.of(AGENT_IDENTITY));
    MtlsInternalServiceAuthenticator authenticator =
        new MtlsInternalServiceAuthenticator(properties);
    MockHttpServletRequest request = requestWithUriSan(AGENT_IDENTITY);

    InternalServicePrincipal principal = authenticator.authenticate(request);

    assertThat(principal.serviceId()).isEqualTo(AGENT_IDENTITY);
    assertThat(principal.scopes()).isEmpty();
  }

  @Test
  void rejectsMissingCertificateAndCertificateForAnotherWorkload() throws Exception {
    AiopsSecurityProperties properties = new AiopsSecurityProperties();
    properties.setInternalAgentCertificateIdentities(List.of(AGENT_IDENTITY));
    MtlsInternalServiceAuthenticator authenticator =
        new MtlsInternalServiceAuthenticator(properties);

    assertThatThrownBy(
            () -> authenticator.authenticate(new MockHttpServletRequest("POST", "/internal/agent")))
        .isInstanceOf(InternalServiceAuthenticationException.class)
        .hasMessageContaining("certificate");
    assertThatThrownBy(
            () ->
                authenticator.authenticate(
                    requestWithUriSan("spiffe://aegisops.local/service/aegisops-app")))
        .isInstanceOf(InternalServiceAuthenticationException.class)
        .hasMessageContaining("identity");
  }

  private MockHttpServletRequest requestWithUriSan(String identity) throws Exception {
    X509Certificate certificate = mock(X509Certificate.class);
    when(certificate.getSubjectAlternativeNames()).thenReturn(List.of(List.of(6, identity)));
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/internal/agent/evidence/query");
    request.setAttribute(
        "jakarta.servlet.request.X509Certificate", new X509Certificate[] {certificate});
    return request;
  }
}

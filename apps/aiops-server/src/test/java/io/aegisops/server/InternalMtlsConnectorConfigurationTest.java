package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.junit.jupiter.api.Test;

class InternalMtlsConnectorConfigurationTest {
  @Test
  void createsDedicatedConnectorThatRequiresAgentCertificate() {
    InternalMtlsProperties properties = new InternalMtlsProperties();
    properties.setPort(8443);
    properties.setCertificateFile("/secrets/app.crt");
    properties.setPrivateKeyFile("/secrets/app.key");
    properties.setClientCaFile("/secrets/agent-client-ca.crt");

    Connector connector = InternalMtlsConnectorConfiguration.createConnector(properties);

    assertThat(connector.getPort()).isEqualTo(8443);
    assertThat(connector.getSecure()).isTrue();
    assertThat(connector.getScheme()).isEqualTo("https");
    SSLHostConfig ssl = connector.findSslHostConfigs()[0];
    assertThat(ssl.getCertificateVerification())
        .isEqualTo(SSLHostConfig.CertificateVerification.REQUIRED);
    assertThat(ssl.getCaCertificateFile()).isEqualTo("/secrets/agent-client-ca.crt");
    assertThat(ssl.getCertificates().iterator().next().getCertificateFile())
        .isEqualTo("/secrets/app.crt");
    assertThat(ssl.getCertificates().iterator().next().getCertificateKeyFile())
        .isEqualTo("/secrets/app.key");
  }
}

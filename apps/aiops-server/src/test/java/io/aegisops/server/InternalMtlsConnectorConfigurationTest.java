package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.security.KeyStore;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.junit.jupiter.api.Test;

class InternalMtlsConnectorConfigurationTest {
  @Test
  void createsDedicatedConnectorThatRequiresAgentCertificate() throws Exception {
    InternalMtlsProperties properties = new InternalMtlsProperties();
    properties.setPort(8443);
    properties.setCertificateFile("/secrets/app.crt");
    properties.setPrivateKeyFile("/secrets/app.key");
    properties.setClientCaFile(
        Path.of(getClass().getResource("/tls/agent-ca.crt").toURI()).toString());

    Connector connector = InternalMtlsConnectorConfiguration.createConnector(properties);

    assertThat(connector.getPort()).isEqualTo(8443);
    assertThat(connector.getSecure()).isTrue();
    assertThat(connector.getScheme()).isEqualTo("https");
    SSLHostConfig ssl = connector.findSslHostConfigs()[0];
    assertThat(ssl.getCertificateVerification())
        .isEqualTo(SSLHostConfig.CertificateVerification.REQUIRED);
    KeyStore trustStore = ssl.getTruststore();
    assertThat(trustStore).isNotNull();
    assertThat(trustStore.size()).isEqualTo(1);
    assertThat(ssl.getCertificates().iterator().next().getCertificateFile())
        .isEqualTo("/secrets/app.crt");
    assertThat(ssl.getCertificates().iterator().next().getCertificateKeyFile())
        .isEqualTo("/secrets/app.key");
  }
}

package io.aegisops.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collection;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InternalMtlsProperties.class)
@ConditionalOnProperty(prefix = "aiops.internal-agent-api", name = "enabled", havingValue = "true")
public class InternalMtlsConnectorConfiguration {
  @Bean
  WebServerFactoryCustomizer<TomcatServletWebServerFactory> internalMtlsConnector(
      InternalMtlsProperties properties) {
    return factory -> factory.addAdditionalConnectors(createConnector(properties));
  }

  static Connector createConnector(InternalMtlsProperties properties) {
    properties.validate();
    Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
    connector.setPort(properties.getPort());
    connector.setScheme("https");
    connector.setSecure(true);
    connector.setProperty("SSLEnabled", "true");

    SSLHostConfig ssl = new SSLHostConfig();
    ssl.setCertificateVerification(SSLHostConfig.CertificateVerification.REQUIRED.name());
    ssl.setTrustStore(loadTrustStore(properties.getClientCaFile()));
    ssl.setProtocols("TLSv1.3,TLSv1.2");
    SSLHostConfigCertificate certificate =
        new SSLHostConfigCertificate(ssl, SSLHostConfigCertificate.Type.UNDEFINED);
    certificate.setCertificateFile(properties.getCertificateFile());
    certificate.setCertificateKeyFile(properties.getPrivateKeyFile());
    ssl.addCertificate(certificate);
    connector.addSslHostConfig(ssl);
    return connector;
  }

  private static KeyStore loadTrustStore(String clientCaFile) {
    try (InputStream input = Files.newInputStream(Path.of(clientCaFile))) {
      Collection<? extends Certificate> certificates =
          CertificateFactory.getInstance("X.509").generateCertificates(input);
      if (certificates.isEmpty()) {
        throw new IllegalStateException("No certificates found in " + clientCaFile);
      }

      KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null, null);
      int index = 0;
      for (Certificate certificate : certificates) {
        trustStore.setCertificateEntry("agent-ca-" + index++, certificate);
      }
      return trustStore;
    } catch (GeneralSecurityException | IOException exception) {
      throw new IllegalStateException(
          "Unable to load Agent client CA from " + clientCaFile, exception);
    }
  }
}

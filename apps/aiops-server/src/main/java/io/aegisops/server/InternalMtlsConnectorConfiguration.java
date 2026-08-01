package io.aegisops.server;

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
    ssl.setCaCertificateFile(properties.getClientCaFile());
    ssl.setProtocols("TLSv1.3,TLSv1.2");
    SSLHostConfigCertificate certificate =
        new SSLHostConfigCertificate(ssl, SSLHostConfigCertificate.Type.UNDEFINED);
    certificate.setCertificateFile(properties.getCertificateFile());
    certificate.setCertificateKeyFile(properties.getPrivateKeyFile());
    ssl.addCertificate(certificate);
    connector.addSslHostConfig(ssl);
    return connector;
  }
}

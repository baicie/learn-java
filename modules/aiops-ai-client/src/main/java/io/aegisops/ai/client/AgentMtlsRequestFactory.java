package io.aegisops.ai.client;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.client.JdkClientHttpRequestFactory;

public final class AgentMtlsRequestFactory {
  private AgentMtlsRequestFactory() {}

  public static JdkClientHttpRequestFactory create(
      AgentClientProperties properties, SslBundles sslBundles) {
    if (!properties.normalizedBaseUrl().startsWith("https://")) {
      throw new IllegalStateException("aiops.agent.base-url must use https when Agent is enabled");
    }
    SslBundle sslBundle = sslBundles.getBundle(properties.normalizedSslBundleName());
    HttpClient httpClient =
        HttpClient.newBuilder()
            .sslContext(sslBundle.createSslContext())
            .connectTimeout(Duration.ofMillis(properties.normalizedConnectTimeoutMillis()))
            .build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(Duration.ofMillis(properties.normalizedReadTimeoutMillis()));
    return factory;
  }
}

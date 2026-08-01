package io.aegisops.ai.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "aiops.agent")
public record AgentClientProperties(
    String baseUrl, Integer connectTimeoutMillis, Integer readTimeoutMillis, String sslBundleName) {
  @ConstructorBinding
  public AgentClientProperties(
      String baseUrl,
      Integer connectTimeoutMillis,
      Integer readTimeoutMillis,
      String sslBundleName) {
    this.baseUrl = baseUrl;
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.readTimeoutMillis = readTimeoutMillis;
    this.sslBundleName = sslBundleName;
  }

  public AgentClientProperties(
      String baseUrl, Integer connectTimeoutMillis, Integer readTimeoutMillis) {
    this(baseUrl, connectTimeoutMillis, readTimeoutMillis, "agent-client");
  }

  public String normalizedBaseUrl() {
    if (baseUrl == null || baseUrl.isBlank()) {
      return "https://localhost:9008";
    }

    String value = baseUrl.trim();
    while (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    return value;
  }

  public int normalizedConnectTimeoutMillis() {
    return connectTimeoutMillis == null || connectTimeoutMillis <= 0 ? 3000 : connectTimeoutMillis;
  }

  public int normalizedReadTimeoutMillis() {
    return readTimeoutMillis == null || readTimeoutMillis <= 0 ? 30000 : readTimeoutMillis;
  }

  public String normalizedSslBundleName() {
    return sslBundleName == null || sslBundleName.isBlank() ? "agent-client" : sslBundleName.trim();
  }
}

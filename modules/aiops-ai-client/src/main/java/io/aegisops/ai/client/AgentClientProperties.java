package io.aegisops.ai.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.agent")
public record AgentClientProperties(
    String baseUrl, String internalToken, Integer connectTimeoutMillis, Integer readTimeoutMillis) {
  public String normalizedBaseUrl() {
    if (baseUrl == null || baseUrl.isBlank()) {
      return "http://localhost:9008";
    }

    String value = baseUrl.trim();
    while (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    return value;
  }

  public String normalizedInternalToken() {
    return internalToken == null || internalToken.isBlank()
        ? "dev-internal-agent-token"
        : internalToken.trim();
  }

  public int normalizedConnectTimeoutMillis() {
    return connectTimeoutMillis == null || connectTimeoutMillis <= 0 ? 3000 : connectTimeoutMillis;
  }

  public int normalizedReadTimeoutMillis() {
    return readTimeoutMillis == null || readTimeoutMillis <= 0 ? 30000 : readTimeoutMillis;
  }
}

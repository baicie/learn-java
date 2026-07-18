package io.aegisops.zabbix;

public record ZabbixConfig(
    String endpoint,
    String username,
    String password,
    String apiToken,
    Integer connectTimeoutSeconds,
    Integer readTimeoutSeconds) {
  public String apiEndpoint() {
    String value = endpoint.trim();
    if (value.endsWith("/api_jsonrpc.php")) {
      return value;
    }
    return value.endsWith("/") ? value + "api_jsonrpc.php" : value + "/api_jsonrpc.php";
  }

  public int connectTimeoutMillis() {
    return clampSeconds(connectTimeoutSeconds, 5, 1, 60) * 1000;
  }

  public int readTimeoutMillis() {
    return clampSeconds(readTimeoutSeconds, 15, 1, 180) * 1000;
  }

  public boolean hasApiToken() {
    return apiToken != null && !apiToken.isBlank();
  }

  public boolean hasUsernamePassword() {
    return username != null && !username.isBlank() && password != null && !password.isBlank();
  }

  public boolean hasAuthentication() {
    return hasApiToken() || hasUsernamePassword();
  }

  private int clampSeconds(Integer value, int defaultValue, int min, int max) {
    int resolved = value == null ? defaultValue : value;
    return Math.max(min, Math.min(max, resolved));
  }
}

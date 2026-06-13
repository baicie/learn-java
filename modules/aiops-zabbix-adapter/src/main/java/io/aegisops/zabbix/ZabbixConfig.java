package io.aegisops.zabbix;

public record ZabbixConfig(
        String endpoint,
        String username,
        String password,
        String apiToken,
        Integer connectTimeoutSeconds,
        Integer readTimeoutSeconds
) {
    public int connectTimeoutMillis() {
        return Math.max(1, connectTimeoutSeconds == null ? 5 : connectTimeoutSeconds) * 1000;
    }

    public int readTimeoutMillis() {
        return Math.max(1, readTimeoutSeconds == null ? 15 : readTimeoutSeconds) * 1000;
    }

    public boolean hasApiToken() {
        return apiToken != null && !apiToken.isBlank();
    }

    public boolean hasUsernamePassword() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }
}

package io.aegisops.datasource;

import jakarta.validation.constraints.NotBlank;

public record ZabbixConfigRequest(
    @NotBlank String endpoint,
    String username,
    String password,
    String apiToken,
    Integer connectTimeoutSeconds,
    Integer readTimeoutSeconds) {}

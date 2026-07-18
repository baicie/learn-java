package io.aegisops.datasource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UpdateDataSourceRequest(
    @NotBlank String name,
    @Valid ZabbixConfig zabbix,
    @Valid KubernetesConfig kubernetes,
    @Valid PassiveConfig passive) {
  public record ZabbixConfig(
      @NotBlank String endpoint,
      String username,
      String password,
      String apiToken,
      @Min(1) Integer connectTimeoutSeconds,
      @Min(1) Integer readTimeoutSeconds) {}

  public record KubernetesConfig(
      @NotBlank String endpoint, String apiToken, @Min(1) Integer timeoutSeconds) {}

  public record PassiveConfig(String endpoint) {}
}

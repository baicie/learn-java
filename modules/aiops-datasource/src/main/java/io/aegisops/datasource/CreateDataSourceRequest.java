package io.aegisops.datasource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record CreateDataSourceRequest(
    @NotBlank String type,
    @NotBlank String name,
    @Valid ZabbixConfigRequest zabbix,
    @Valid KubernetesConfigRequest kubernetes,
    @Valid PassiveDataSourceConfigRequest passive) {}

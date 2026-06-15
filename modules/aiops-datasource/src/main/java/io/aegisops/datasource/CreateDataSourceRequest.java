package io.aegisops.datasource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDataSourceRequest(
    @NotBlank String type, @NotBlank String name, @Valid @NotNull ZabbixConfigRequest zabbix) {}

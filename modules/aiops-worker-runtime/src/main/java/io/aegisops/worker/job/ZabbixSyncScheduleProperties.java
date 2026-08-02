package io.aegisops.worker.job;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "aiops.zabbix-sync")
public record ZabbixSyncScheduleProperties(
    boolean enabled, @Min(100) long pollDelayMs, @Min(1) long cadenceMs, @Min(1) int batchSize) {}

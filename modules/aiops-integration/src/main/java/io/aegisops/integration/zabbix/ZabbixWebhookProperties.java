package io.aegisops.integration.zabbix;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.integrations.zabbix.webhook")
public record ZabbixWebhookProperties(String token) {}

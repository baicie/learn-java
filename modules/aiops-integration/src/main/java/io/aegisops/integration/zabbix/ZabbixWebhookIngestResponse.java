package io.aegisops.integration.zabbix;

public record ZabbixWebhookIngestResponse(
    String alertId,
    String datasourceId,
    String tenantId,
    String sourceEventId,
    String status,
    boolean created,
    String message) {}

package io.aegisops.integration.application.port;

public interface ZabbixWebhookDatasourceStore {
  boolean existsZabbix(String tenantId, String datasourceId);
}

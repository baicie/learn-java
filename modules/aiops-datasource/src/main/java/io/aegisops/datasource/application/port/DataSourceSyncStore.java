package io.aegisops.datasource.application.port;

import io.aegisops.kubernetes.domain.model.KubernetesConfig;
import io.aegisops.zabbix.ZabbixConfig;
import java.util.Map;

public interface DataSourceSyncStore {
  boolean isPending(String tenantId, String datasourceId, String runId);

  void start(String tenantId, String datasourceId, String runId);

  ZabbixConfig loadZabbixConfig(String tenantId, String datasourceId);

  KubernetesConfig loadKubernetesConfig(String tenantId, String datasourceId);

  void complete(String tenantId, String datasourceId, String runId, Map<String, Object> statistics);

  void fail(
      String tenantId,
      String datasourceId,
      String runId,
      Map<String, Object> statistics,
      String message);
}

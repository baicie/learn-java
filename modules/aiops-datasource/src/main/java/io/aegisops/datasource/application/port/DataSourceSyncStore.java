package io.aegisops.datasource.application.port;

import io.aegisops.kubernetes.domain.model.KubernetesConfig;
import io.aegisops.zabbix.ZabbixConfig;
import java.time.OffsetDateTime;
import java.util.Map;

public interface DataSourceSyncStore {
  boolean createZabbixManualRun(String tenantId, String datasourceId, String runId);

  boolean createLegacyManualRun(String tenantId, String datasourceId, String runId);

  boolean isPending(String tenantId, String datasourceId, String runId);

  void start(String tenantId, String datasourceId, String runId);

  SyncRunClaimResult claimForExecution(
      String tenantId, String datasourceId, String runId, String claimToken);

  boolean renewClaim(
      String tenantId,
      String datasourceId,
      String runId,
      String claimToken,
      OffsetDateTime leaseUntil);

  ZabbixConfig loadZabbixConfig(String tenantId, String datasourceId);

  KubernetesConfig loadKubernetesConfig(String tenantId, String datasourceId);

  void complete(String tenantId, String datasourceId, String runId, Map<String, Object> statistics);

  boolean completeClaimed(
      String tenantId,
      String datasourceId,
      String runId,
      String claimToken,
      Map<String, Object> statistics);

  void fail(
      String tenantId,
      String datasourceId,
      String runId,
      Map<String, Object> statistics,
      String message);

  boolean failClaimed(
      String tenantId,
      String datasourceId,
      String runId,
      String claimToken,
      Map<String, Object> statistics,
      String message);

  enum SyncRunClaimResult {
    ACQUIRED,
    ALREADY_COMPLETED,
    ACTIVE,
    NOT_FOUND
  }
}

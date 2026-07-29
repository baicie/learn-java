package io.aegisops.datasource.application;

import io.aegisops.common.exception.AppException;
import io.aegisops.common.outbox.OutboxMessage;
import io.aegisops.common.outbox.OutboxWriter;
import io.aegisops.datasource.api.dto.StartSyncResponse;
import io.aegisops.datasource.application.port.DataSourceSyncStore;
import io.aegisops.datasource.application.port.ZabbixSyncDispatchStore;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ManualDataSourceSyncApplicationService {
  private static final String SOURCE_ZABBIX = "zabbix";
  private static final String SOURCE_KUBERNETES = "kubernetes";

  private final OutboxWriter outboxWriter;
  private final DataSourceSyncStore syncStore;
  private final ZabbixSyncDispatchStore zabbixSyncDispatchStore;

  public ManualDataSourceSyncApplicationService(
      OutboxWriter outboxWriter,
      DataSourceSyncStore syncStore,
      ZabbixSyncDispatchStore zabbixSyncDispatchStore) {
    this.outboxWriter = outboxWriter;
    this.syncStore = syncStore;
    this.zabbixSyncDispatchStore = zabbixSyncDispatchStore;
  }

  public StartSyncResponse start(String tenantId, String datasourceId, String sourceType) {
    String jobName = syncJobName(sourceType);
    if (SOURCE_ZABBIX.equals(sourceType)) {
      zabbixSyncDispatchStore.failPendingRunsWithTerminalDispatchFailure(tenantId, datasourceId);
    }
    String runId = newId("sync");
    boolean created =
        SOURCE_ZABBIX.equals(sourceType)
            ? syncStore.createZabbixManualRun(tenantId, datasourceId, runId)
            : syncStore.createLegacyManualRun(tenantId, datasourceId, runId);
    if (!created) {
      throw new AppException(
          "DATASOURCE_SYNC_ALREADY_RUNNING", "Datasource already has an active sync run");
    }
    Map<String, Object> payload =
        Map.of("tenantId", tenantId, "datasourceId", datasourceId, "runId", runId);
    outboxWriter.enqueue(
        new OutboxMessage(
            tenantId,
            "worker",
            jobName,
            payload,
            jobName + ":" + tenantId + ":" + datasourceId + ":" + runId,
            3,
            null));
    return new StartSyncResponse(runId, "pending");
  }

  private String syncJobName(String sourceType) {
    return switch (sourceType) {
      case SOURCE_ZABBIX -> "zabbix-sync";
      case SOURCE_KUBERNETES -> "kubernetes-sync";
      default -> throw new AppException("UNSUPPORTED_DATASOURCE", "Datasource cannot be synced");
    };
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}

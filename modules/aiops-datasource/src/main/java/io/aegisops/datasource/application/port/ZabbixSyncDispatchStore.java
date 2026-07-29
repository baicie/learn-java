package io.aegisops.datasource.application.port;

import java.time.OffsetDateTime;
import java.util.List;

public interface ZabbixSyncDispatchStore {
  List<SyncTarget> lockDueTargets(OffsetDateTime dueBefore, int batchSize);

  int failPendingRunsWithTerminalDispatchFailure(String tenantId, String datasourceId);

  boolean createScheduledRun(
      String runId, String tenantId, String datasourceId, OffsetDateTime startedAt);

  record SyncTarget(String tenantId, String datasourceId) {}
}

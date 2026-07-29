package io.aegisops.datasource.application;

import io.aegisops.common.outbox.OutboxMessage;
import io.aegisops.common.outbox.OutboxWriter;
import io.aegisops.datasource.application.port.ZabbixSyncDispatchStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ZabbixSyncDispatchApplicationService {
  private static final String JOB_NAME = "zabbix-sync";
  private static final String TARGET_APP = "worker";

  private final ZabbixSyncDispatchStore store;
  private final OutboxWriter outboxWriter;

  public ZabbixSyncDispatchApplicationService(
      ZabbixSyncDispatchStore store, OutboxWriter outboxWriter) {
    this.store = store;
    this.outboxWriter = outboxWriter;
  }

  @Transactional
  public int dispatchDue(OffsetDateTime now, Duration cadence, int batchSize) {
    if (now == null) {
      throw new IllegalArgumentException("now is required");
    }
    if (cadence == null || cadence.isNegative() || cadence.isZero() || cadence.toMillis() == 0) {
      throw new IllegalArgumentException("cadence must be positive");
    }
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be positive");
    }

    long bucket = Math.floorDiv(now.toInstant().toEpochMilli(), cadence.toMillis());
    int dispatched = 0;
    for (var target : store.lockDueTargets(now.minus(cadence), batchSize)) {
      store.failPendingRunsWithTerminalDispatchFailure(target.tenantId(), target.datasourceId());
      String bucketDigest =
          digest(target.tenantId(), target.datasourceId(), cadence.toMillis(), bucket);
      String runId = "sync_" + bucketDigest;
      if (!store.createScheduledRun(runId, target.tenantId(), target.datasourceId(), now)) {
        continue;
      }
      Map<String, Object> payload =
          Map.of(
              "tenantId", target.tenantId(),
              "datasourceId", target.datasourceId(),
              "runId", runId);
      outboxWriter.enqueue(
          new OutboxMessage(
              target.tenantId(),
              TARGET_APP,
              JOB_NAME,
              payload,
              "zabbix-sync-scheduled:" + bucketDigest + ":" + bucket,
              3,
              now));
      dispatched++;
    }
    return dispatched;
  }

  private String digest(String tenantId, String datasourceId, long cadenceMillis, long bucket) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] value =
          digest.digest(
              (tenantId + "\u0000" + datasourceId + "\u0000" + cadenceMillis + "\u0000" + bucket)
                  .getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(value, 0, 16);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}

package io.aegisops.worker.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.datasource.application.DataSourceSyncApplicationService;
import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class ZabbixSyncJob implements OutboxJob {
  public static final String JOB_NAME = "zabbix-sync";
  private final DataSourceSyncApplicationService service;
  private final ObjectMapper objectMapper;

  public ZabbixSyncJob(DataSourceSyncApplicationService service, ObjectMapper objectMapper) {
    this.service = service;
    this.objectMapper = objectMapper;
  }

  @Override
  public String jobName() {
    return JOB_NAME;
  }

  @Override
  public JobResult handle(AutomationOutboxRecord row) {
    try {
      SyncPayload payload = payload(row);
      if (!payload.tenantId().equals(row.getTenantId())) {
        return JobResult.failure("TENANT_MISMATCH");
      }
      service.execute(
          payload.tenantId(), payload.datasourceId(), payload.runId(), requiredClaimToken(row));
      return JobResult.success();
    } catch (Exception exception) {
      return JobResult.failure(exception.getClass().getSimpleName());
    }
  }

  @Override
  public boolean renewLease(AutomationOutboxRecord row, OffsetDateTime leaseUntil) {
    try {
      SyncPayload payload = payload(row);
      if (!payload.tenantId().equals(row.getTenantId())) {
        return false;
      }
      return service.renewLease(
          payload.tenantId(),
          payload.datasourceId(),
          payload.runId(),
          requiredClaimToken(row),
          leaseUntil);
    } catch (Exception exception) {
      return false;
    }
  }

  private SyncPayload payload(AutomationOutboxRecord row) throws Exception {
    JsonNode payload = objectMapper.readTree(row.getPayload().data());
    return new SyncPayload(
        required(payload, "tenantId"),
        required(payload, "datasourceId"),
        required(payload, "runId"));
  }

  private String requiredClaimToken(AutomationOutboxRecord row) {
    String claimToken = row.getClaimToken();
    if (claimToken == null || claimToken.isBlank()) {
      throw new IllegalArgumentException("claimToken is required");
    }
    return claimToken;
  }

  private String required(JsonNode payload, String field) {
    String value = payload.path(field).asText().trim();
    if (value.isEmpty()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value;
  }

  private record SyncPayload(String tenantId, String datasourceId, String runId) {}
}

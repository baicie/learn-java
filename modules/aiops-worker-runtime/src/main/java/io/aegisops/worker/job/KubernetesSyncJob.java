package io.aegisops.worker.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.datasource.application.KubernetesSyncApplicationService;
import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import org.springframework.stereotype.Component;

@Component
public class KubernetesSyncJob implements OutboxJob {
  public static final String JOB_NAME = "kubernetes-sync";
  private final KubernetesSyncApplicationService service;
  private final ObjectMapper objectMapper;

  public KubernetesSyncJob(KubernetesSyncApplicationService service, ObjectMapper objectMapper) {
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
      JsonNode payload = objectMapper.readTree(row.getPayload().data());
      String tenantId = required(payload, "tenantId");
      if (!tenantId.equals(row.getTenantId())) return JobResult.failure("TENANT_MISMATCH");
      service.execute(tenantId, required(payload, "datasourceId"), required(payload, "runId"));
      return JobResult.success();
    } catch (Exception exception) {
      return JobResult.failure(exception.getClass().getSimpleName());
    }
  }

  private String required(JsonNode payload, String field) {
    String value = payload.path(field).asText().trim();
    if (value.isEmpty()) throw new IllegalArgumentException(field + " is required");
    return value;
  }
}

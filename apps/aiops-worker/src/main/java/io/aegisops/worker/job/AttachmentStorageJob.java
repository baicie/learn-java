package io.aegisops.worker.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import io.aegisops.workrecord.application.service.AttachmentStorageProcessor;
import org.springframework.stereotype.Component;

@Component
public class AttachmentStorageJob implements OutboxJob {
  private final AttachmentStorageProcessor processor;
  private final ObjectMapper objectMapper;

  public AttachmentStorageJob(AttachmentStorageProcessor processor, ObjectMapper objectMapper) {
    this.processor = processor;
    this.objectMapper = objectMapper;
  }

  @Override
  public String jobName() {
    return "work-record-attachment-storage";
  }

  @Override
  public JobResult handle(AutomationOutboxRecord row) {
    try {
      JsonNode payload = objectMapper.readTree(row.getPayload().data());
      String tenantId = required(payload, "tenantId");
      if (!tenantId.equals(row.getTenantId())) {
        return JobResult.failure("TENANT_MISMATCH");
      }
      processor.process(
          tenantId,
          required(payload, "attachmentId"),
          required(payload, "action"),
          required(payload, "objectKey"));
      return JobResult.success();
    } catch (Exception ex) {
      return JobResult.failure(ex.getClass().getSimpleName());
    }
  }

  private static String required(JsonNode payload, String field) {
    String value = payload.path(field).asText().trim();
    if (value.isEmpty()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value;
  }
}

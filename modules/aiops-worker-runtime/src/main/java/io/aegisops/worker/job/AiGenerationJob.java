package io.aegisops.worker.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import io.aegisops.workrecord.application.service.AiGenerationProcessor;
import org.springframework.stereotype.Component;

@Component
public class AiGenerationJob implements OutboxJob {
  public static final String JOB_NAME = "work-record-ai-generate";
  private final AiGenerationProcessor processor;
  private final ObjectMapper objectMapper;

  public AiGenerationJob(AiGenerationProcessor processor, ObjectMapper objectMapper) {
    this.processor = processor;
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
      if (!tenantId.equals(row.getTenantId())) {
        return JobResult.failure("TENANT_MISMATCH");
      }
      boolean finalAttempt = row.getRetryCount() + 1 >= row.getMaxRetries();
      processor.process(tenantId, required(payload, "generationId"), finalAttempt);
      return JobResult.success();
    } catch (Exception ex) {
      return JobResult.failure(ex.getClass().getSimpleName());
    }
  }

  private static String required(JsonNode node, String field) {
    String value = node.path(field).asText().trim();
    if (value.isEmpty()) {
      throw new IllegalArgumentException(field + " is required");
    }
    return value;
  }
}

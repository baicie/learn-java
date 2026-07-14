package io.aegisops.worker.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import io.aegisops.security.UserPrincipalFactory;
import io.aegisops.user.UserAccount;
import io.aegisops.user.UserService;
import io.aegisops.workrecord.application.service.AsyncExportProcessor;
import org.springframework.stereotype.Component;

@Component
public class AsyncExportJob implements OutboxJob {
  public static final String JOB_NAME = "work-record-async-export";

  private final AsyncExportProcessor processor;
  private final UserService users;
  private final UserPrincipalFactory principals;
  private final ObjectMapper objectMapper;

  public AsyncExportJob(
      AsyncExportProcessor processor,
      UserService users,
      UserPrincipalFactory principals,
      ObjectMapper objectMapper) {
    this.processor = processor;
    this.users = users;
    this.principals = principals;
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
      String jobId = required(payload, "jobId");
      String requestedBy = required(payload, "requestedBy");
      if (!tenantId.equals(row.getTenantId())) {
        return JobResult.failure("TENANT_MISMATCH");
      }
      UserAccount account = users.getById(requestedBy);
      if (!tenantId.equals(account.tenantId())) {
        return JobResult.failure("USER_TENANT_MISMATCH");
      }
      processor.process(tenantId, jobId, principals.create(account));
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

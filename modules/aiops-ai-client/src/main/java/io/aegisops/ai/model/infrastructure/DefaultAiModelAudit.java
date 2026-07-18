package io.aegisops.ai.model.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.model.api.AiModelResponse;
import io.aegisops.ai.model.port.AiModelAudit;
import io.aegisops.audit.AuditRecordCommand;
import io.aegisops.audit.AuditService;
import org.springframework.stereotype.Component;

@Component
public class DefaultAiModelAudit implements AiModelAudit {
  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public DefaultAiModelAudit(AuditService auditService, ObjectMapper objectMapper) {
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @Override
  public void record(
      String tenantId, String actorId, String action, String resourceId, AiModelResponse snapshot) {
    auditService.record(
        new AuditRecordCommand(
            tenantId, actorId, action, "ai_model", resourceId, "{}", json(snapshot), "{}"));
  }

  private String json(AiModelResponse snapshot) {
    try {
      return objectMapper.writeValueAsString(snapshot);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("AI 模型审计快照序列化失败", exception);
    }
  }
}

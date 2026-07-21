package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.workrecord.WorkRecordAiClient;
import io.aegisops.ai.client.workrecord.WorkRecordGenerationRequest;
import io.aegisops.workrecord.application.port.AiGenerationRepository;
import io.aegisops.workrecord.application.port.AiGenerationRepository.CompleteGeneration;
import io.aegisops.workrecord.domain.model.AiGeneration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiGenerationProcessor {
  private final AiGenerationRepository generations;
  private final WorkRecordAiClient client;
  private final ObjectMapper objectMapper;
  private final WorkRecordAuditService audit;

  public AiGenerationProcessor(
      AiGenerationRepository generations,
      WorkRecordAiClient client,
      ObjectMapper objectMapper,
      WorkRecordAuditService audit) {
    this.generations = generations;
    this.client = client;
    this.objectMapper = objectMapper;
    this.audit = audit;
  }

  public void process(String tenantId, String id, boolean finalAttempt) {
    var generation =
        generations
            .find(tenantId, id)
            .orElseThrow(() -> new IllegalArgumentException("AI generation not found"));
    if (!generations.markRunning(tenantId, id)) {
      return;
    }
    try {
      var request =
          objectMapper.readValue(generation.inputJson(), WorkRecordGenerationRequest.class);
      var response = client.generate(request);
      if (!generations.complete(
          new CompleteGeneration(
              tenantId,
              id,
              response.markdown(),
              response.provider(),
              response.model(),
              response.providerRunId(),
              response.providerWorkflowId(),
              response.providerWorkflowVersion(),
              response.providerDurationMs(),
              response.providerTotalTokens(),
              objectMapper.writeValueAsString(response.warnings()),
              response.fallbackReason()))) {
        throw new IllegalStateException("AI generation state changed");
      }
      auditOutcome(generation, response.fallbackReason(), response.provider(), response.providerRunId());
    } catch (RuntimeException ex) {
      markAttemptFailed(generation, finalAttempt, ex);
      throw ex;
    } catch (Exception ex) {
      markAttemptFailed(generation, finalAttempt, ex);
      throw new IllegalStateException("invalid AI generation input", ex);
    }
  }

  private void markAttemptFailed(AiGeneration generation, boolean finalAttempt, Exception error) {
    if (finalAttempt) {
      generations.fail(generation.tenantId(), generation.id());
      audit(
          generation,
          WorkRecordAuditActions.AI_GENERATION_FAILED,
          Map.of("errorType", error.getClass().getSimpleName()));
    } else {
      generations.markRetrying(generation.tenantId(), generation.id());
    }
  }

  private void auditOutcome(
      AiGeneration generation, String fallbackReason, String provider, String providerRunId) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("provider", provider);
    if (providerRunId != null && !providerRunId.isBlank()) {
      detail.put("providerRunId", providerRunId);
    }
    String action = WorkRecordAuditActions.AI_GENERATION_SUCCEEDED;
    if (fallbackReason != null && !fallbackReason.isBlank()) {
      action = WorkRecordAuditActions.AI_GENERATION_FALLBACK;
      detail.put("fallbackReason", fallbackReason);
    }
    audit(generation, action, detail);
  }

  private void audit(AiGeneration generation, String action, Map<String, Object> detail) {
    try {
      audit.record(
          generation.tenantId(),
          null,
          null,
          "work_record_ai_generation",
          generation.id(),
          action,
          generation.requestedBy(),
          objectMapper.writeValueAsString(detail));
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("failed to serialize AI generation audit", ex);
    }
  }
}

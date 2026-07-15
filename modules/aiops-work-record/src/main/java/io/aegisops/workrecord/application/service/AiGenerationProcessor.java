package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.workrecord.WorkRecordAiClient;
import io.aegisops.ai.client.workrecord.WorkRecordGenerationRequest;
import io.aegisops.workrecord.application.port.AiGenerationRepository;
import org.springframework.stereotype.Service;

@Service
public class AiGenerationProcessor {
  private final AiGenerationRepository generations;
  private final WorkRecordAiClient client;
  private final ObjectMapper objectMapper;

  public AiGenerationProcessor(
      AiGenerationRepository generations, WorkRecordAiClient client, ObjectMapper objectMapper) {
    this.generations = generations;
    this.client = client;
    this.objectMapper = objectMapper;
  }

  public void process(String tenantId, String id) {
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
          tenantId, id, response.markdown(), response.provider(), response.model())) {
        throw new IllegalStateException("AI generation state changed");
      }
    } catch (RuntimeException ex) {
      generations.fail(tenantId, id);
      throw ex;
    } catch (Exception ex) {
      generations.fail(tenantId, id);
      throw new IllegalStateException("invalid AI generation input", ex);
    }
  }
}

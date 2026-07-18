package io.aegisops.ai.model.port;

import io.aegisops.ai.model.api.AiModelResponse;

public interface AiModelAudit {
  void record(
      String tenantId, String actorId, String action, String resourceId, AiModelResponse snapshot);
}

package io.aegisops.ai.model.port;

import io.aegisops.ai.model.domain.AiModelConfig;
import java.util.List;
import java.util.Optional;

public interface AiModelStore {
  List<AiModelConfig> list(String tenantId);

  Optional<AiModelConfig> find(String tenantId, String id);

  AiModelConfig insert(AiModelConfig model);

  AiModelConfig update(AiModelConfig model);

  void delete(String tenantId, String id);

  AiModelConfig setDefault(String tenantId, String id);

  AiModelConfig updateTestResult(String tenantId, String id, String status, String message);
}

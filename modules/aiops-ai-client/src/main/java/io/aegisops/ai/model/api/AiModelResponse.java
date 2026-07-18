package io.aegisops.ai.model.api;

import io.aegisops.ai.model.domain.AiModelConfig;
import java.time.OffsetDateTime;

public record AiModelResponse(
    String id,
    String provider,
    String name,
    String modelName,
    String baseUrl,
    boolean enabled,
    boolean defaultModel,
    boolean apiKeyConfigured,
    String lastTestStatus,
    String lastTestMessage,
    OffsetDateTime lastTestedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static AiModelResponse from(AiModelConfig model) {
    return new AiModelResponse(
        model.id(),
        model.provider(),
        model.name(),
        model.modelName(),
        model.baseUrl(),
        model.enabled(),
        model.defaultModel(),
        model.encryptedApiKey() != null && !model.encryptedApiKey().isBlank(),
        model.lastTestStatus(),
        model.lastTestMessage(),
        model.lastTestedAt(),
        model.createdAt(),
        model.updatedAt());
  }
}

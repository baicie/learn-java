package io.aegisops.ai.model.domain;

import java.time.OffsetDateTime;

public record AiModelConfig(
    String id,
    String tenantId,
    String provider,
    String name,
    String modelName,
    String baseUrl,
    String encryptedApiKey,
    boolean enabled,
    boolean defaultModel,
    String lastTestStatus,
    String lastTestMessage,
    OffsetDateTime lastTestedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

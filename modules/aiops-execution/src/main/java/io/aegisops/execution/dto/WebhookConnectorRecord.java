package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record WebhookConnectorRecord(
    String id,
    String tenantId,
    String name,
    String description,
    String baseUrl,
    String defaultMethod,
    String defaultHeadersJson,
    String sensitiveHeadersJson,
    boolean enabled,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

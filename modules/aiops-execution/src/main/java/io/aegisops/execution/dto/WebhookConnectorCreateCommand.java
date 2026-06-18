package io.aegisops.execution.dto;

public record WebhookConnectorCreateCommand(
    String id,
    String tenantId,
    String name,
    String description,
    String baseUrl,
    String defaultMethod,
    String defaultHeadersJson,
    String sensitiveHeadersJson,
    boolean enabled,
    String createdBy) {}

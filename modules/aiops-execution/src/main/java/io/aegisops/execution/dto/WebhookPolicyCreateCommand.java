package io.aegisops.execution.dto;

public record WebhookPolicyCreateCommand(
    String id,
    String tenantId,
    String connectorId,
    boolean allowLive,
    String allowedHostsJson,
    String allowedMethodsJson,
    boolean blockPrivateIp,
    boolean blockLocalhost,
    boolean blockMetadataIp,
    int maxBodyBytes,
    int timeoutMillis,
    boolean enabled) {}

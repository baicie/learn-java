package io.aegisops.execution.dto;

import java.time.OffsetDateTime;

public record WebhookPolicyRecord(
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
    boolean enabled,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

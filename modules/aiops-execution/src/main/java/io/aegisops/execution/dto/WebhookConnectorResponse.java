package io.aegisops.execution.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record WebhookConnectorResponse(
    String id,
    String name,
    String description,
    String baseUrl,
    String defaultMethod,
    Map<String, String> defaultHeaders,
    List<String> sensitiveHeaders,
    boolean enabled,
    boolean allowLive,
    List<String> allowedHosts,
    List<String> allowedMethods,
    boolean blockPrivateIp,
    boolean blockLocalhost,
    boolean blockMetadataIp,
    int maxBodyBytes,
    int timeoutMillis,
    String createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}

package io.aegisops.execution.dto;

import java.util.List;
import java.util.Map;

public record WebhookConnectorCreateRequest(
    String name,
    String description,
    String baseUrl,
    String defaultMethod,
    Map<String, String> defaultHeaders,
    List<String> sensitiveHeaders,
    List<String> allowedHosts,
    List<String> allowedMethods,
    Boolean allowLive,
    Integer maxBodyBytes,
    Integer timeoutMillis,
    String createdBy) {}

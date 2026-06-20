package io.aegisops.security;

public record TenantSecurityEventCreateCommand(
    String id,
    String tenantId,
    String eventType,
    String severity,
    String actor,
    String requestPath,
    String remoteAddr,
    String summary,
    String metadataJson) {}

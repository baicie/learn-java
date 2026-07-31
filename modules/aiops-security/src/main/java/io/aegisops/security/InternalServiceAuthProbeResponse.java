package io.aegisops.security;

public record InternalServiceAuthProbeResponse(
    boolean ok, String serviceId, String tenantId, String incidentId, String traceId) {}

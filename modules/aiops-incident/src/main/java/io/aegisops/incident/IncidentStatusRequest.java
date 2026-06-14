package io.aegisops.incident;

public record IncidentStatusRequest(
        String status,
        String note
) {}

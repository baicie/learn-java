package io.aegisops.incident;

/** Bundles parameters for test helper IncidentSummaryRecord construction. */
public record SummaryParams(
    String id,
    String tenantId,
    String title,
    String severity,
    String status,
    String aggregationKey,
    int alertCount) {}

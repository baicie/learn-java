package io.aegisops.incident;

public record IncidentAggregationResponse(
    int scannedAlerts, int groups, int incidentsCreated, int incidentsUpdated, int alertsLinked) {}

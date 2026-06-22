package io.aegisops.incident;

public record IncidentAggregationResponse(
    int alertsScanned, int groups, int incidentsCreated, int incidentsUpdated, int alertsLinked) {}

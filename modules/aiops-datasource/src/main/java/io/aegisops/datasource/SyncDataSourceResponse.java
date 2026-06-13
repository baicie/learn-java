package io.aegisops.datasource;

public record SyncDataSourceResponse(
        String runId,
        String status,
        int hostsCreated,
        int hostsUpdated,
        int alertsCreated,
        int alertsUpdated,
        String message
) {}

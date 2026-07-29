package io.aegisops.datasource.application;

record DataSourceSyncClaim(String tenantId, String datasourceId, String runId, String claimToken) {}

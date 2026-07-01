package io.aegisops.server.workbench;

public record WorkbenchSummary(
    long activeIncidents,
    long criticalAlerts,
    long todayNewAlerts,
    long datasourceErrors,
    long pendingTasks,
    String moduleHealth) {}

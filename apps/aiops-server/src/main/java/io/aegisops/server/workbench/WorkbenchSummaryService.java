package io.aegisops.server.workbench;

import io.aegisops.platform.PlatformModuleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WorkbenchSummaryService {
  private final JdbcTemplate jdbc;
  private final PlatformModuleRepository moduleRepository;

  public WorkbenchSummaryService(JdbcTemplate jdbc, PlatformModuleRepository moduleRepository) {
    this.jdbc = jdbc;
    this.moduleRepository = moduleRepository;
  }

  public WorkbenchSummary summary(String tenantId) {
    long activeIncidents = count("incident", tenantId, "status not in ('resolved','closed','ignored')");
    long criticalAlerts =
        count("alert_event", tenantId, "severity in ('high','disaster') and status = 'open'");
    long todayNewAlerts =
        countSince("alert_event", tenantId, "created_at", java.time.LocalDate.now().toString());
    long datasourceErrors = count("datasource", tenantId, "status = 'error'");
    long pendingTasks = count("automation_job", tenantId, "status = 'waiting_approval'");
    String moduleHealth =
        moduleRepository.findAll().stream()
                .anyMatch(m -> "UNHEALTHY".equals(m.healthStatus()))
            ? "UNHEALTHY"
            : "HEALTHY";
    return new WorkbenchSummary(
        activeIncidents, criticalAlerts, todayNewAlerts, datasourceErrors, pendingTasks, moduleHealth);
  }

  private long count(String table, String tenantId, String where) {
    String sql = "select count(*) from " + table + " where tenant_id = ?";
    if (where != null && !where.isEmpty()) {
      sql += " and " + where;
    }
    Long value = jdbc.queryForObject(sql, Long.class, tenantId);
    return value == null ? 0L : value;
  }

  private long countSince(String table, String tenantId, String dateCol, String since) {
    String sql = "select count(*) from " + table + " where tenant_id = ? and " + dateCol + " >= ?";
    Long value = jdbc.queryForObject(sql, Long.class, tenantId, since);
    return value == null ? 0L : value;
  }
}

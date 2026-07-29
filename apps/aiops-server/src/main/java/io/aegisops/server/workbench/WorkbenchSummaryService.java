package io.aegisops.server.workbench;

import io.aegisops.platform.PlatformModuleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WorkbenchSummaryService {
  private final JdbcTemplate jdbc;
  private final PlatformModuleRepository moduleRepository;
  private final Clock clock;

  @Autowired
  public WorkbenchSummaryService(JdbcTemplate jdbc, PlatformModuleRepository moduleRepository) {
    this(jdbc, moduleRepository, Clock.systemDefaultZone());
  }

  WorkbenchSummaryService(
      JdbcTemplate jdbc, PlatformModuleRepository moduleRepository, Clock clock) {
    this.jdbc = jdbc;
    this.moduleRepository = moduleRepository;
    this.clock = clock;
  }

  public WorkbenchSummary summary(String tenantId) {
    OffsetDateTime todayStart =
        LocalDate.now(clock).atStartOfDay(clock.getZone()).toOffsetDateTime();

    long activeIncidents =
        count("incident", tenantId, "status not in ('resolved','closed','ignored')");
    long criticalAlerts =
        count(
            "alert_event",
            tenantId,
            "severity in ('high','critical','disaster') and status = 'open'");
    long todayNewAlerts = countSince("alert_event", tenantId, "created_at", todayStart);
    long datasourceErrors = count("datasource", tenantId, "status = 'error'");

    // Phase 1 only scaffolds the platform; the automation_job table is not part of MVP.
    long pendingTasks = 0L;

    String moduleHealth =
        moduleRepository.findAll().stream()
                .anyMatch(module -> "UNHEALTHY".equals(module.healthStatus()))
            ? "UNHEALTHY"
            : "HEALTHY";

    return new WorkbenchSummary(
        activeIncidents,
        criticalAlerts,
        todayNewAlerts,
        datasourceErrors,
        pendingTasks,
        moduleHealth);
  }

  private long count(String table, String tenantId, String where) {
    String sql = "select count(*) from " + table + " where tenant_id = ?";
    if (where != null && !where.isEmpty()) {
      sql += " and " + where;
    }
    Long value = jdbc.queryForObject(sql, Long.class, tenantId);
    return value == null ? 0L : value;
  }

  private long countSince(String table, String tenantId, String dateCol, OffsetDateTime since) {
    String sql = "select count(*) from " + table + " where tenant_id = ? and " + dateCol + " >= ?";
    Long value = jdbc.queryForObject(sql, Long.class, tenantId, since);
    return value == null ? 0L : value;
  }
}

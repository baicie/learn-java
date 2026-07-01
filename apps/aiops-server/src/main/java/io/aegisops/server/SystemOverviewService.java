package io.aegisops.server;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SystemOverviewService {
  private final JdbcTemplate jdbc;
  private final String appName;

  public SystemOverviewService(
      JdbcTemplate jdbc, @Value("${spring.application.name}") String appName) {
    this.jdbc = jdbc;
    this.appName = appName;
  }

  public Map<String, Object> overview(String tenantId) {
    return Map.of(
        "app", appName,
        "tenants", 1L,
        "users", count("select count(*) from sys_user where tenant_id = ?", tenantId),
        "assets", count("select count(*) from asset where tenant_id = ?", tenantId),
        "alerts", count("select count(*) from alert_event where tenant_id = ?", tenantId),
        "incidents", count("select count(*) from incident where tenant_id = ?", tenantId));
  }

  private Long count(String sql, String tenantId) {
    Long value = jdbc.queryForObject(sql, Long.class, tenantId);
    return value == null ? 0L : value;
  }
}

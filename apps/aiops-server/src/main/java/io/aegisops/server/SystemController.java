package io.aegisops.server;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {
  private final JdbcTemplate jdbc;
  private final String appName;

  public SystemController(JdbcTemplate jdbc, @Value("${spring.application.name}") String appName) {
    this.jdbc = jdbc;
    this.appName = appName;
  }

  @GetMapping("/overview")
  public ApiResponse<Map<String, Object>> overview() {
    String tenantId = TenantContext.requireTenantId();
    Long tenants = 1L;
    Long users = count("select count(*) from sys_user where tenant_id = ?", tenantId);
    Long assets = count("select count(*) from asset where tenant_id = ?", tenantId);
    Long alerts = count("select count(*) from alert_event where tenant_id = ?", tenantId);
    Long incidents = count("select count(*) from incident where tenant_id = ?", tenantId);
    return ApiResponse.ok(
        Map.of(
            "app", appName,
            "tenants", tenants,
            "users", users,
            "assets", assets,
            "alerts", alerts,
            "incidents", incidents));
  }

  private Long count(String sql, String tenantId) {
    Long value = jdbc.queryForObject(sql, Long.class, tenantId);
    return value == null ? 0L : value;
  }
}

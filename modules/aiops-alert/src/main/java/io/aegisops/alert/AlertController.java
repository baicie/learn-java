package io.aegisops.alert;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
  private final JdbcTemplate jdbc;

  public AlertController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping
  public ApiResponse<List<AlertEventRecord>> list() {
    String tenantId = TenantContext.requireTenantId();
    return ApiResponse.ok(
        jdbc.query(
            """
                select id, tenant_id, source, severity, title, status, starts_at, created_at
                from alert_event where tenant_id = ? order by starts_at desc limit 100
                """,
            (rs, rowNum) ->
                new AlertEventRecord(
                    rs.getString("id"),
                    rs.getString("tenant_id"),
                    rs.getString("source"),
                    rs.getString("severity"),
                    rs.getString("title"),
                    rs.getString("status"),
                    rs.getObject("starts_at", OffsetDateTime.class),
                    rs.getObject("created_at", OffsetDateTime.class)),
            tenantId));
  }
}

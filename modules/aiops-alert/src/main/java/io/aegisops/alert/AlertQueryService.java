package io.aegisops.alert;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AlertQueryService {
  private final JdbcTemplate jdbc;

  public AlertQueryService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<AlertEventRecord> listRecent(String tenantId) {
    return jdbc.query(
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
        tenantId);
  }
}

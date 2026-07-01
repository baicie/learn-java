package io.aegisops.audit;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditQueryService {
  private final JdbcTemplate jdbc;

  public AuditQueryService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<AuditLog> listRecent(String tenantId) {
    return jdbc.query(
        """
            select id, tenant_id, actor_user_id, action, target_type, target_id, detail_json::text, created_at
            from audit_log where tenant_id = ? order by created_at desc limit 100
            """,
        (rs, rowNum) ->
            new AuditLog(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("actor_user_id"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                rs.getString("detail_json"),
                rs.getObject("created_at", OffsetDateTime.class)),
        tenantId);
  }
}

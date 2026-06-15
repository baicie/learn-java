package io.aegisops.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
  private final JdbcTemplate jdbc;

  public AuditService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void record(AuditRecordCommand command) {
    jdbc.update(
        """
                insert into audit_log(id, tenant_id, actor_user_id, action, target_type, target_id, detail_json)
                values (?, ?, ?, ?, ?, ?, ?::jsonb)
                """,
        io.aegisops.common.id.Ids.newId(),
        command.tenantId(),
        command.actorUserId(),
        command.action(),
        command.targetType(),
        command.targetId(),
        command.detailJson());
  }
}

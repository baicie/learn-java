package io.aegisops.audit;

import io.aegisops.common.id.Ids;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String tenantId, String actorUserId, String action, String targetType, String targetId, String detailJson) {
        jdbc.update("""
                insert into audit_log(id, tenant_id, actor_user_id, action, target_type, target_id, detail_json)
                values (?, ?, ?, ?, ?, ?, ?::jsonb)
                """, Ids.newId(), tenantId, actorUserId, action, targetType, targetId, detailJson == null ? "{}" : detailJson);
    }
}

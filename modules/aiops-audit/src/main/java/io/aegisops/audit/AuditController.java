package io.aegisops.audit;

import io.aegisops.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditController {
    private final JdbcTemplate jdbc;

    public AuditController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ApiResponse<List<AuditLog>> list() {
        return ApiResponse.ok(jdbc.query("""
                select id, tenant_id, actor_user_id, action, target_type, target_id, detail_json::text, created_at
                from audit_log order by created_at desc limit 100
                """, (rs, rowNum) -> new AuditLog(
                rs.getString("id"), rs.getString("tenant_id"), rs.getString("actor_user_id"), rs.getString("action"),
                rs.getString("target_type"), rs.getString("target_id"), rs.getString("detail_json"),
                rs.getObject("created_at", OffsetDateTime.class)
        )));
    }
}

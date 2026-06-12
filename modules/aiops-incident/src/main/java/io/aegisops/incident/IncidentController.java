package io.aegisops.incident;

import io.aegisops.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {
    private final JdbcTemplate jdbc;

    public IncidentController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ApiResponse<List<IncidentRecord>> list() {
        return ApiResponse.ok(jdbc.query("""
                select id, tenant_id, title, severity, status, started_at, created_at from incident order by started_at desc limit 100
                """, (rs, rowNum) -> new IncidentRecord(
                rs.getString("id"), rs.getString("tenant_id"), rs.getString("title"), rs.getString("severity"),
                rs.getString("status"), rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class)
        )));
    }
}

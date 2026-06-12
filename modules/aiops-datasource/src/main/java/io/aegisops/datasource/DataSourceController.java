package io.aegisops.datasource;

import io.aegisops.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/datasources")
public class DataSourceController {
    private final JdbcTemplate jdbc;

    public DataSourceController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ApiResponse<List<DataSourceRecord>> list() {
        return ApiResponse.ok(jdbc.query("""
                select id, tenant_id, type, name, status, created_at, updated_at from datasource order by created_at desc
                """, (rs, rowNum) -> new DataSourceRecord(
                rs.getString("id"), rs.getString("tenant_id"), rs.getString("type"), rs.getString("name"),
                rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class)
        )));
    }
}

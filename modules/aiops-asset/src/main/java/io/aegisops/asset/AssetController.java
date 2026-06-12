package io.aegisops.asset;

import io.aegisops.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/assets")
public class AssetController {
    private final JdbcTemplate jdbc;

    public AssetController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ApiResponse<List<AssetRecord>> list() {
        return ApiResponse.ok(jdbc.query("""
                select id, tenant_id, asset_type, name, display_name, source, status, created_at from asset order by created_at desc limit 100
                """, (rs, rowNum) -> new AssetRecord(
                rs.getString("id"), rs.getString("tenant_id"), rs.getString("asset_type"), rs.getString("name"),
                rs.getString("display_name"), rs.getString("source"), rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class)
        )));
    }
}

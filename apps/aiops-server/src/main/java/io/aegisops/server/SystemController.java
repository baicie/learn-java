package io.aegisops.server;

import io.aegisops.common.api.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
        Long tenants = jdbc.queryForObject("select count(*) from tenant", Long.class);
        Long users = jdbc.queryForObject("select count(*) from sys_user", Long.class);
        Long assets = jdbc.queryForObject("select count(*) from asset", Long.class);
        Long alerts = jdbc.queryForObject("select count(*) from alert_event", Long.class);
        Long incidents = jdbc.queryForObject("select count(*) from incident", Long.class);
        return ApiResponse.ok(Map.of(
                "app", appName,
                "tenants", tenants,
                "users", users,
                "assets", assets,
                "alerts", alerts,
                "incidents", incidents
        ));
    }
}

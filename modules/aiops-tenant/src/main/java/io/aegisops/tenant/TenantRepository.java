package io.aegisops.tenant;

import io.aegisops.common.id.Ids;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class TenantRepository {
    private final JdbcTemplate jdbc;

    private final RowMapper<Tenant> mapper = (rs, rowNum) -> new Tenant(
            rs.getString("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("status"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
    );

    public TenantRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Tenant> findAll() {
        return jdbc.query("select id, code, name, status, created_at, updated_at from tenant order by created_at desc", mapper);
    }

    public Optional<Tenant> findByCode(String code) {
        List<Tenant> rows = jdbc.query("select id, code, name, status, created_at, updated_at from tenant where code = ?", mapper, code);
        return rows.stream().findFirst();
    }

    public Tenant create(String code, String name) {
        String id = Ids.newId();
        jdbc.update("insert into tenant(id, code, name, status) values (?, ?, ?, 'active')", id, code, name);
        return findByCode(code).orElseThrow();
    }
}

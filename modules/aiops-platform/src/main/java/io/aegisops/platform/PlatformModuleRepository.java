package io.aegisops.platform;

import io.aegisops.common.id.Ids;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformModuleRepository {
  private final JdbcTemplate jdbc;

  private final RowMapper<PlatformModule> mapper =
      (rs, rowNum) ->
          new PlatformModule(
              rs.getString("id"),
              rs.getString("module_id"),
              rs.getString("name"),
              rs.getString("version"),
              rs.getBoolean("enabled"),
              rs.getString("health_status"),
              rs.getString("config_json"),
              rs.getObject("created_at", OffsetDateTime.class));

  public PlatformModuleRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<PlatformModule> findAll() {
    return jdbc.query(
        """
            select id, module_id, name, version, enabled, health_status, config_json, created_at
            from platform_module order by created_at asc
            """,
        mapper);
  }

  public Optional<PlatformModule> findByModuleId(String moduleId) {
    List<PlatformModule> rows =
        jdbc.query(
            """
                select id, module_id, name, version, enabled, health_status, config_json, created_at
                from platform_module where module_id = ?
                """,
            mapper,
            moduleId);
    return rows.stream().findFirst();
  }

  public PlatformModule create(String moduleId, String name, String version) {
    String id = Ids.newId();
    jdbc.update(
        """
            insert into platform_module(id, module_id, name, version, enabled, health_status, config_json)
            values (?, ?, ?, ?, true, 'UNKNOWN', '{}')
            """,
        id,
        moduleId,
        name,
        version);
    return findByModuleId(moduleId).orElseThrow();
  }

  public void updateHealthStatus(String moduleId, String healthStatus) {
    jdbc.update(
        "update platform_module set health_status = ? where module_id = ?", healthStatus, moduleId);
  }
}

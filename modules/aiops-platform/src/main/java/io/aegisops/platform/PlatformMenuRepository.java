package io.aegisops.platform;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformMenuRepository {
  private final JdbcTemplate jdbc;

  public PlatformMenuRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<PlatformMenuItem> listEnabled(Collection<String> authorities) {
    List<String> permissions = authorities == null ? List.of() : authorities.stream().toList();
    return jdbc.query(
        """
            select id, module_id, parent_id, path, title, icon,
                   permission_code, sort_order, enabled, created_at
            from platform_menu_item
            where enabled = true
              and (
                permission_code is null
                or permission_code = ''
                or permission_code = any (?::varchar[])
              )
            order by sort_order asc, created_at asc
            """,
        (rs, rowNum) ->
            new PlatformMenuItem(
                rs.getString("id"),
                rs.getString("module_id"),
                rs.getString("parent_id"),
                rs.getString("path"),
                rs.getString("title"),
                rs.getString("icon"),
                rs.getString("permission_code"),
                rs.getInt("sort_order"),
                rs.getBoolean("enabled"),
                rs.getObject("created_at", OffsetDateTime.class)),
        permissions.toArray(String[]::new));
  }
}

package io.aegisops.platform;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkspaceRepository {
  private final JdbcTemplate jdbc;

  public WorkspaceRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<WorkspaceRecord> listByUser(String tenantId, String userId) {
    return jdbc.query(
        """
            select w.id, w.tenant_id, w.code, w.name, w.enabled, w.created_at
            from sys_workspace w
            where w.tenant_id = ?
              and w.enabled = true
              and (
                exists (
                  select 1 from sys_workspace_member m
                  where m.workspace_id = w.id and m.user_id = ?
                )
                or not exists (
                  select 1 from sys_workspace_member m
                  where m.workspace_id = w.id
                )
              )
            order by w.created_at asc
            """,
        (rs, rowNum) ->
            new WorkspaceRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getBoolean("enabled"),
                rs.getObject("created_at", OffsetDateTime.class)),
        tenantId,
        userId);
  }
}

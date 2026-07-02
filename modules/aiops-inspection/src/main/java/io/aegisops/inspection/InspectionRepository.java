package io.aegisops.inspection;

import io.aegisops.common.id.Ids;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InspectionRepository {
  private final JdbcTemplate jdbc;

  public InspectionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public InspectionTaskRecord create(String tenantId, CreateInspectionTaskRequest request, String createdBy) {
    String id = Ids.newId();
    jdbc.update(
        """
            insert into inspection_task(id, tenant_id, name, target_type, target_query, template_key, created_by)
            values (?, ?, ?, ?, ?::jsonb, ?, ?)
            """,
        id,
        tenantId,
        request.name(),
        request.targetType(),
        blankJson(request.targetQueryJson()),
        request.templateKey(),
        createdBy);
    return find(tenantId, id);
  }

  public List<InspectionTaskRecord> list(String tenantId) {
    return jdbc.query(
        """
            select id, tenant_id, name, target_type, target_query::text, template_key,
                   enabled, created_by, created_at, updated_at
            from inspection_task
            where tenant_id = ?
            order by created_at desc
            """,
        (rs, rowNum) -> mapTask(rs),
        tenantId);
  }

  public InspectionTaskRecord find(String tenantId, String id) {
    return jdbc.queryForObject(
        """
            select id, tenant_id, name, target_type, target_query::text, template_key,
                   enabled, created_by, created_at, updated_at
            from inspection_task
            where tenant_id = ? and id = ?
            """,
        (rs, rowNum) -> mapTask(rs),
        tenantId,
        id);
  }

  private InspectionTaskRecord mapTask(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new InspectionTaskRecord(
        rs.getString(1),
        rs.getString(2),
        rs.getString(3),
        rs.getString(4),
        rs.getString(5),
        rs.getString(6),
        rs.getBoolean(7),
        rs.getString(8),
        rs.getObject(9, OffsetDateTime.class),
        rs.getObject(10, OffsetDateTime.class));
  }

  private String blankJson(String value) {
    return value == null || value.isBlank() ? "{}" : value;
  }
}

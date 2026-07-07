package io.aegisops.workrecord;

import io.aegisops.common.id.Ids;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkRecordTemplateRepository {
  private final JdbcTemplate jdbc;

  public WorkRecordTemplateRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<WorkRecordTemplate> list(String tenantId) {
    return jdbc.query(
        """
            select id, tenant_id, name, code, description, enabled, schema_json::text,
                   created_by, created_at, updated_at
            from wr_template
            where tenant_id = ?
            order by created_at desc
            """,
        (rs, rowNum) -> mapTemplate(rs),
        tenantId);
  }

  public Optional<WorkRecordTemplate> find(String tenantId, String id) {
    List<WorkRecordTemplate> rows =
        jdbc.query(
            """
                select id, tenant_id, name, code, description, enabled, schema_json::text,
                       created_by, created_at, updated_at
                from wr_template
                where tenant_id = ? and id = ?
                """,
            (rs, rowNum) -> mapTemplate(rs),
            tenantId,
            id);
    return rows.stream().findFirst();
  }

  public WorkRecordTemplate create(
      String tenantId, CreateTemplateRequest request, String createdBy) {
    String id = Ids.newId();
    jdbc.update(
        """
            insert into wr_template(
              id, tenant_id, name, code, description, enabled, schema_json, created_by)
            values (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            """,
        id,
        tenantId,
        request.name(),
        request.code(),
        request.description(),
        request.enabled() == null || request.enabled(),
        blankJson(request.schemaJson()),
        createdBy);
    return find(tenantId, id).orElseThrow();
  }

  public Optional<WorkRecordTemplate> update(
      String tenantId, String id, UpdateTemplateRequest request) {
    jdbc.update(
        """
            update wr_template
               set name        = coalesce(?, name),
                   description = coalesce(?, description),
                   enabled     = coalesce(?, enabled),
                   schema_json = coalesce(?::jsonb, schema_json),
                   updated_at  = now()
             where tenant_id = ? and id = ?
            """,
        request.name(),
        request.description(),
        request.enabled(),
        nullableJson(request.schemaJson()),
        tenantId,
        id);
    return find(tenantId, id);
  }

  private WorkRecordTemplate mapTemplate(ResultSet rs) throws SQLException {
    return new WorkRecordTemplate(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("name"),
        rs.getString("code"),
        rs.getString("description"),
        rs.getBoolean("enabled"),
        rs.getString("schema_json"),
        rs.getString("created_by"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }

  private String blankJson(String value) {
    return value == null || value.isBlank() ? "{}" : value;
  }

  private String nullableJson(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value;
  }
}

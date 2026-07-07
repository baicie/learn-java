package io.aegisops.workrecord;

import io.aegisops.common.id.Ids;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkRecordFieldRepository {
  private final JdbcTemplate jdbc;

  public WorkRecordFieldRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<WorkRecordField> list(String tenantId, String templateId) {
    return jdbc.query(
        """
            select id, tenant_id, template_id, field_name, field_code, field_type,
                   required, default_value, option_source, dict_code, options_json::text,
                   list_visible, filterable, statistical, sort_order, enabled, created_at, updated_at
            from wr_template_field
            where tenant_id = ? and template_id = ?
            order by sort_order asc, created_at asc
            """,
        (rs, rowNum) -> mapField(rs),
        tenantId,
        templateId);
  }

  public WorkRecordField create(String tenantId, String templateId, CreateFieldRequest request) {
    String id = Ids.newId();
    jdbc.update(
        """
            insert into wr_template_field(
              id, tenant_id, template_id, field_name, field_code, field_type, required,
              default_value, option_source, dict_code, options_json, list_visible,
              filterable, statistical, sort_order, enabled)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
            """,
        id,
        tenantId,
        templateId,
        request.fieldName(),
        request.fieldCode(),
        request.fieldType(),
        request.required() != null && request.required(),
        request.defaultValue(),
        request.optionSource() == null ? "static" : request.optionSource(),
        request.dictCode(),
        blankArray(request.optionsJson()),
        request.listVisible() != null && request.listVisible(),
        request.filterable() != null && request.filterable(),
        request.statistical() != null && request.statistical(),
        request.sortOrder() == null ? 0 : request.sortOrder(),
        request.enabled() == null || request.enabled());
    return list(tenantId, templateId).stream()
        .filter(field -> field.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  public Optional<WorkRecordField> update(
      String tenantId, String templateId, String fieldId, UpdateFieldRequest request) {
    int rows =
        jdbc.update(
            """
                update wr_template_field
                   set field_name    = coalesce(?, field_name),
                       required      = coalesce(?, required),
                       default_value = coalesce(?, default_value),
                       option_source = coalesce(?, option_source),
                       dict_code     = coalesce(?, dict_code),
                       options_json  = coalesce(?::jsonb, options_json),
                       list_visible  = coalesce(?, list_visible),
                       filterable    = coalesce(?, filterable),
                       statistical   = coalesce(?, statistical),
                       sort_order    = coalesce(?, sort_order),
                       enabled       = coalesce(?, enabled),
                       updated_at    = now()
                 where tenant_id = ? and template_id = ? and id = ?
                """,
            request.fieldName(),
            request.required(),
            request.defaultValue(),
            request.optionSource(),
            request.dictCode(),
            nullableArray(request.optionsJson()),
            request.listVisible(),
            request.filterable(),
            request.statistical(),
            request.sortOrder(),
            request.enabled(),
            tenantId,
            templateId,
            fieldId);
    if (rows == 0) {
      return Optional.empty();
    }
    return list(tenantId, templateId).stream()
        .filter(field -> field.id().equals(fieldId))
        .findFirst();
  }

  @Transactional
  public void replace(String tenantId, String templateId, List<CreateFieldRequest> requests) {
    jdbc.update(
        "delete from wr_template_field where tenant_id = ? and template_id = ?",
        tenantId,
        templateId);
    if (requests == null) {
      return;
    }
    for (CreateFieldRequest request : requests) {
      create(tenantId, templateId, request);
    }
  }

  private WorkRecordField mapField(ResultSet rs) throws SQLException {
    return new WorkRecordField(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("template_id"),
        rs.getString("field_name"),
        rs.getString("field_code"),
        rs.getString("field_type"),
        rs.getBoolean("required"),
        rs.getString("default_value"),
        rs.getString("option_source"),
        rs.getString("dict_code"),
        rs.getString("options_json"),
        rs.getBoolean("list_visible"),
        rs.getBoolean("filterable"),
        rs.getBoolean("statistical"),
        rs.getInt("sort_order"),
        rs.getBoolean("enabled"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }

  private String blankArray(String value) {
    return value == null || value.isBlank() ? "[]" : value;
  }

  /**
   * 用于 update 路径：{@code null} 透传给 SQL，让 {@code coalesce(?::jsonb, ...)} 保留现有 值。空串同样视为
   * null，避免被解析成空数组。
   */
  private String nullableArray(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value;
  }
}

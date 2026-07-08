package io.aegisops.workrecord.infrastructure.persistence;

import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.api.dto.CreateFieldRequest;
import io.aegisops.workrecord.api.dto.UpdateFieldRequest;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
                   list_visible, filterable, exportable, statistical, sort_order, enabled,
                   schema_path, created_at, updated_at
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
    String schemaPath =
        request.schemaPath() != null && !request.schemaPath().isBlank()
            ? request.schemaPath()
            : defaultSchemaPath(request.fieldCode());
    jdbc.update(
        """
            insert into wr_template_field(
              id, tenant_id, template_id, field_name, field_code, field_type, required,
              default_value, option_source, dict_code, options_json, list_visible,
              filterable, exportable, statistical, sort_order, enabled, schema_path)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
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
        request.exportable() == null || request.exportable(),
        request.statistical() != null && request.statistical(),
        request.sortOrder() == null ? 0 : request.sortOrder(),
        request.enabled() == null || request.enabled(),
        schemaPath);
    return list(tenantId, templateId).stream()
        .filter(field -> field.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  /**
   * 批量创建字段索引记录，用于从 Formily schema 同步字段时的批量插入。
   *
   * <p>不清理现有字段（由调用方自行调用 replace 或逐个 updateEnabled）。
   *
   * @param tenantId 租户 ID
   * @param templateId 模板 ID
   * @param requests 字段创建请求列表（已按 sortOrder 排序）
   */
  @Transactional
  public void createInBatch(String tenantId, String templateId, List<CreateFieldRequest> requests) {
    if (requests == null || requests.isEmpty()) {
      return;
    }
    for (CreateFieldRequest request : requests) {
      create(tenantId, templateId, request);
    }
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
                       exportable    = coalesce(?, exportable),
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
            request.exportable(),
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

  /** 软禁用字段（enabled=false），用于 schema 同步时删除不再出现的字段。 */
  public void updateEnabled(String tenantId, String templateId, String fieldId, boolean enabled) {
    jdbc.update(
        """
            update wr_template_field
               set enabled   = ?,
                   updated_at = now()
             where tenant_id = ? and template_id = ? and id = ?
            """,
        enabled,
        tenantId,
        templateId,
        fieldId);
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
        rs.getBoolean("exportable"),
        rs.getBoolean("statistical"),
        rs.getInt("sort_order"),
        rs.getBoolean("enabled"),
        rs.getString("schema_path"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }

  private String blankArray(String value) {
    return value == null || value.isBlank() ? "[]" : value;
  }

  /**
   * @see #nullableArray(String)
   */
  private String nullableArray(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value;
  }

  /**
   * 默认 schema path 规则：{@code .properties.<fieldCode>}。
   *
   * <p>字段在 Formily schema 的 {@code properties} 节点下以 fieldCode 为 key， 因此 path 对应 {@code
   * .properties.<fieldCode>}。若设计器支持嵌套 schema， 由 CreateFieldRequest.schemaPath 传入显式路径。
   */
  static String defaultSchemaPath(String fieldCode) {
    return ".properties." + (fieldCode == null ? "" : fieldCode);
  }
}

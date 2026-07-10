package io.aegisops.workrecord.infrastructure.jdbc;

import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.application.command.TemplateFieldIndexEntry;
import io.aegisops.workrecord.application.port.WorkRecordFieldIndexRepository;
import io.aegisops.workrecord.domain.model.FieldType;
import io.aegisops.workrecord.domain.model.OptionSource;
import io.aegisops.workrecord.domain.model.WorkRecordField;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkRecordFieldIndexRepository implements WorkRecordFieldIndexRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcWorkRecordFieldIndexRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void createForVersion(
      String tenantId,
      String templateId,
      String templateVersionId,
      List<TemplateFieldIndexEntry> fields) {
    for (TemplateFieldIndexEntry field : fields) {
      Map<String, Object> params = new HashMap<>();
      params.put("id", Ids.newId());
      params.put("tenantId", tenantId);
      params.put("templateId", templateId);
      params.put("versionId", templateVersionId);
      params.put("fieldName", field.fieldName());
      params.put("fieldCode", field.fieldCode());
      params.put("fieldType", field.fieldType().value());
      params.put("required", field.required());
      params.put("defaultValue", field.defaultValue());
      params.put("optionSource", field.optionSource().value());
      params.put("dictCode", nullable(field.dictCode()));
      params.put("options", field.optionsJson() == null ? "[]" : field.optionsJson());
      params.put("schemaPath", nullable(field.schemaPath()));
      params.put("listVisible", field.listVisible());
      params.put("filterable", field.filterable());
      params.put("exportable", field.exportable());
      params.put("statistical", field.statistical());
      params.put("sortOrder", field.sortOrder());
      params.put("enabled", field.enabled());

      jdbc.update(
          """
          insert into work_record.wr_template_field(
            id, tenant_id, template_id, template_version_id,
            field_name, field_code, field_type, required, default_value,
            option_source, dict_code, options_json, schema_path,
            list_visible, filterable, exportable, statistical, sort_order, enabled)
          values (
            :id, :tenantId, :templateId, :versionId,
            :fieldName, :fieldCode, :fieldType, :required, :defaultValue,
            :optionSource, :dictCode, cast(:options as jsonb), :schemaPath,
            :listVisible, :filterable, :exportable, :statistical, :sortOrder, :enabled)
          """,
          params);
    }
  }

  @Override
  public List<WorkRecordField> listByVersion(String tenantId, String templateVersionId) {
    return listFields(tenantId, templateVersionId, false);
  }

  @Override
  public List<WorkRecordField> listByVersions(String tenantId, List<String> templateVersionIds) {
    if (templateVersionIds == null || templateVersionIds.isEmpty()) {
      return List.of();
    }
    return jdbc.query(
        """
        select id, tenant_id, template_id, template_version_id, field_name, field_code, field_type,
               required, default_value, option_source, dict_code, options_json::text, schema_path,
               list_visible, filterable, exportable, statistical, sort_order, enabled,
               created_at, updated_at
          from work_record.wr_template_field
         where tenant_id = :tenantId
           and template_version_id in (:versionIds)
         order by template_version_id, sort_order asc, field_code asc
        """,
        Map.of("tenantId", tenantId, "versionIds", templateVersionIds),
        (rs, rowNum) -> mapField(rs));
  }

  @Override
  public List<WorkRecordField> listEnabledByVersion(String tenantId, String templateVersionId) {
    return listFields(tenantId, templateVersionId, true);
  }

  @Override
  public List<WorkRecordField> listEnabledByVersions(
      String tenantId, List<String> templateVersionIds) {
    return listFieldsAcrossVersions(tenantId, templateVersionIds, false);
  }

  @Override
  public List<WorkRecordField> listFilterableByVersions(
      String tenantId, List<String> templateVersionIds) {
    return listFieldsAcrossVersions(tenantId, templateVersionIds, true);
  }

  private List<WorkRecordField> listFieldsAcrossVersions(
      String tenantId, List<String> templateVersionIds, boolean filterableOnly) {
    if (templateVersionIds == null || templateVersionIds.isEmpty()) {
      return List.of();
    }
    String sql =
        """
        select id, tenant_id, template_id, template_version_id, field_name, field_code, field_type,
               required, default_value, option_source, dict_code, options_json::text, schema_path,
               list_visible, filterable, exportable, statistical, sort_order, enabled,
               created_at, updated_at
          from work_record.wr_template_field
         where tenant_id = :tenantId
           and template_version_id in (:versionIds)
           and enabled = true
        """
            + (filterableOnly ? " and filterable = true " : "")
            + " order by sort_order asc, field_code asc ";
    return jdbc.query(
        sql,
        Map.of("tenantId", tenantId, "versionIds", templateVersionIds),
        (rs, rowNum) -> mapField(rs));
  }

  private List<WorkRecordField> listFields(
      String tenantId, String templateVersionId, boolean onlyEnabled) {
    return jdbc.query(
        """
        select id, tenant_id, template_id, template_version_id, field_name, field_code, field_type,
               required, default_value, option_source, dict_code, options_json::text, schema_path,
               list_visible, filterable, exportable, statistical, sort_order, enabled,
               created_at, updated_at
          from work_record.wr_template_field
         where tenant_id = :tenantId
           and template_version_id = :versionId
           and (:onlyEnabled = false or enabled = true)
         order by sort_order asc, created_at asc
        """,
        Map.of("tenantId", tenantId, "versionId", templateVersionId, "onlyEnabled", onlyEnabled),
        (rs, rowNum) -> mapField(rs));
  }

  private WorkRecordField mapField(ResultSet rs) throws SQLException {
    return new WorkRecordField(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("template_id"),
        rs.getString("template_version_id"),
        rs.getString("field_name"),
        rs.getString("field_code"),
        FieldType.from(rs.getString("field_type")),
        rs.getBoolean("required"),
        rs.getString("default_value"),
        OptionSource.from(rs.getString("option_source")),
        rs.getString("dict_code"),
        rs.getString("options_json"),
        rs.getString("schema_path"),
        rs.getBoolean("list_visible"),
        rs.getBoolean("filterable"),
        rs.getBoolean("exportable"),
        rs.getBoolean("statistical"),
        rs.getInt("sort_order"),
        rs.getBoolean("enabled"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }

  private Object nullable(Object value) {
    return value == null ? null : value;
  }
}

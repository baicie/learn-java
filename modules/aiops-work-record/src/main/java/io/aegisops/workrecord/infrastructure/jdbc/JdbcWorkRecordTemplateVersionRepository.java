package io.aegisops.workrecord.infrastructure.jdbc;

import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.application.port.WorkRecordTemplateVersionRepository;
import io.aegisops.workrecord.domain.model.WorkRecordTemplateVersion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkRecordTemplateVersionRepository
    implements WorkRecordTemplateVersionRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcWorkRecordTemplateVersionRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public int nextVersionNo(String tenantId, String templateId) {
    Integer value =
        jdbc.queryForObject(
            """
            select coalesce(max(version_no), 0) + 1
              from work_record.wr_template_version
             where tenant_id = :tenantId and template_id = :templateId
            """,
            Map.of("tenantId", tenantId, "templateId", templateId),
            Integer.class);
    return value == null ? 1 : value;
  }

  @Override
  public WorkRecordTemplateVersion create(
      String tenantId,
      String templateId,
      int versionNo,
      String versionName,
      String schemaJson,
      String designerJson,
      String fieldIndexJson,
      String actor) {
    String id = Ids.newId();

    Map<String, Object> params = new HashMap<>();
    params.put("id", id);
    params.put("tenantId", tenantId);
    params.put("templateId", templateId);
    params.put("versionNo", versionNo);
    params.put("versionName", versionName);
    params.put("schema", blankJson(schemaJson));
    params.put("designer", blankJson(designerJson));
    params.put(
        "fieldIndex", fieldIndexJson == null || fieldIndexJson.isBlank() ? "[]" : fieldIndexJson);
    params.put("actor", actorOrSystem(actor));

    jdbc.update(
        """
        insert into work_record.wr_template_version(
          id, tenant_id, template_id, version_no, version_name,
          schema_json, designer_json, field_index_json, published_by)
        values (
          :id, :tenantId, :templateId, :versionNo, :versionName,
          cast(:schema as jsonb), cast(:designer as jsonb), cast(:fieldIndex as jsonb), :actor)
        """,
        params);
    return find(tenantId, id).orElseThrow();
  }

  @Override
  public Optional<WorkRecordTemplateVersion> find(String tenantId, String versionId) {
    List<WorkRecordTemplateVersion> rows =
        jdbc.query(
            """
            select id, tenant_id, template_id, version_no, version_name,
                   schema_json::text, designer_json::text, field_index_json::text,
                   published_by, published_at, created_at
              from work_record.wr_template_version
             where tenant_id = :tenantId and id = :id
            """,
            Map.of("tenantId", tenantId, "id", versionId),
            (rs, rowNum) -> mapVersion(rs));
    return rows.stream().findFirst();
  }

  @Override
  public Optional<WorkRecordTemplateVersion> findByTemplateAndVersion(
      String tenantId, String templateId, String versionId) {
    List<WorkRecordTemplateVersion> rows =
        jdbc.query(
            """
            select id, tenant_id, template_id, version_no, version_name,
                   schema_json::text, designer_json::text, field_index_json::text,
                   published_by, published_at, created_at
              from work_record.wr_template_version
             where tenant_id = :tenantId
               and template_id = :templateId
               and id = :versionId
            """,
            Map.of("tenantId", tenantId, "templateId", templateId, "versionId", versionId),
            (rs, rowNum) -> mapVersion(rs));
    return rows.stream().findFirst();
  }

  @Override
  public Optional<WorkRecordTemplateVersion> findCurrent(String tenantId, String templateId) {
    List<WorkRecordTemplateVersion> rows =
        jdbc.query(
            """
            select v.id, v.tenant_id, v.template_id, v.version_no, v.version_name,
                   v.schema_json::text, v.designer_json::text, v.field_index_json::text,
                   v.published_by, v.published_at, v.created_at
              from work_record.wr_template t
              join work_record.wr_template_version v
                on v.tenant_id = t.tenant_id
               and v.template_id = t.id
               and v.id = t.current_version_id
             where t.tenant_id = :tenantId
               and t.id = :templateId
               and t.deleted_at is null
            """,
            Map.of("tenantId", tenantId, "templateId", templateId),
            (rs, rowNum) -> mapVersion(rs));
    return rows.stream().findFirst();
  }

  @Override
  public List<WorkRecordTemplateVersion> list(String tenantId, String templateId) {
    return jdbc.query(
        """
        select id, tenant_id, template_id, version_no, version_name,
               schema_json::text, designer_json::text, field_index_json::text,
               published_by, published_at, created_at
          from work_record.wr_template_version
         where tenant_id = :tenantId and template_id = :templateId
         order by version_no desc
        """,
        Map.of("tenantId", tenantId, "templateId", templateId),
        (rs, rowNum) -> mapVersion(rs));
  }

  private WorkRecordTemplateVersion mapVersion(ResultSet rs) throws SQLException {
    return new WorkRecordTemplateVersion(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("template_id"),
        rs.getInt("version_no"),
        rs.getString("version_name"),
        rs.getString("schema_json"),
        rs.getString("designer_json"),
        rs.getString("field_index_json"),
        rs.getString("published_by"),
        rs.getObject("published_at", OffsetDateTime.class),
        rs.getObject("created_at", OffsetDateTime.class));
  }

  private String blankJson(String value) {
    return value == null || value.isBlank() ? "{}" : value;
  }

  private String actorOrSystem(String actor) {
    return actor == null || actor.isBlank() ? "system" : actor;
  }
}

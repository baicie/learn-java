package io.aegisops.workrecord.infrastructure.jdbc;

import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.application.command.CreateTemplateCommand;
import io.aegisops.workrecord.application.command.UpdateTemplateCommand;
import io.aegisops.workrecord.application.command.UpdateTemplateDraftCommand;
import io.aegisops.workrecord.application.port.WorkRecordTemplateRepository;
import io.aegisops.workrecord.domain.model.TemplateStatus;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
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
public class JdbcWorkRecordTemplateRepository implements WorkRecordTemplateRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcWorkRecordTemplateRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<WorkRecordTemplate> list(String tenantId, boolean includeDisabled) {
    return jdbc.query(
        """
        select id, tenant_id, code, name, description, status, enabled, current_version_id,
               draft_schema_json::text, draft_designer_json::text,
               created_by, created_at, updated_at, deleted_at
          from work_record.wr_template
         where tenant_id = :tenantId
           and deleted_at is null
           and (:includeDisabled = true or enabled = true)
         order by updated_at desc
        """,
        Map.of("tenantId", tenantId, "includeDisabled", includeDisabled),
        (rs, rowNum) -> mapTemplate(rs));
  }

  @Override
  public Optional<WorkRecordTemplate> find(String tenantId, String templateId) {
    List<WorkRecordTemplate> rows =
        jdbc.query(
            """
            select id, tenant_id, code, name, description, status, enabled, current_version_id,
                   draft_schema_json::text, draft_designer_json::text,
                   created_by, created_at, updated_at, deleted_at
              from work_record.wr_template
             where tenant_id = :tenantId and id = :id and deleted_at is null
            """,
            Map.of("tenantId", tenantId, "id", templateId),
            (rs, rowNum) -> mapTemplate(rs));
    return rows.stream().findFirst();
  }

  @Override
  public Optional<WorkRecordTemplate> findByCode(String tenantId, String code) {
    List<WorkRecordTemplate> rows =
        jdbc.query(
            """
            select id, tenant_id, code, name, description, status, enabled, current_version_id,
                   draft_schema_json::text, draft_designer_json::text,
                   created_by, created_at, updated_at, deleted_at
              from work_record.wr_template
             where tenant_id = :tenantId
               and code = :code
               and deleted_at is null
            """,
            Map.of("tenantId", tenantId, "code", code),
            (rs, rowNum) -> mapTemplate(rs));
    return rows.stream().findFirst();
  }

  @Override
  public WorkRecordTemplate create(String tenantId, CreateTemplateCommand command, String actor) {
    String id = Ids.newId();
    Map<String, Object> params = new HashMap<>();
    params.put("id", id);
    params.put("tenantId", tenantId);
    params.put("code", command.code());
    params.put("name", command.name());
    params.put("description", nullable(command.description()));
    params.put("schema", blankJson(command.draftSchemaJson()));
    params.put("designer", blankJson(command.draftDesignerJson()));
    params.put("actor", actorOrSystem(actor));
    jdbc.update(
        """
        insert into work_record.wr_template(
          id, tenant_id, code, name, description, status, enabled,
          draft_schema_json, draft_designer_json, created_by)
        values (
          :id, :tenantId, :code, :name, :description, 'draft', true,
          :schema::jsonb, :designer::jsonb, :actor)
        """,
        params);
    return find(tenantId, id).orElseThrow();
  }

  @Override
  public WorkRecordTemplate update(
      String tenantId, String templateId, UpdateTemplateCommand command) {
    Map<String, Object> params = new HashMap<>();
    params.put("tenantId", tenantId);
    params.put("id", templateId);
    params.put("name", command.name());
    params.put("description", command.description());

    jdbc.update(
        """
        update work_record.wr_template
           set name = coalesce(:name, name),
               description = coalesce(:description, description)
         where tenant_id = :tenantId
           and id = :id
           and deleted_at is null
        """,
        params);

    return find(tenantId, templateId).orElseThrow();
  }

  @Override
  public WorkRecordTemplate updateDraft(
      String tenantId, String templateId, UpdateTemplateDraftCommand command) {
    Map<String, Object> params = new HashMap<>();
    params.put("tenantId", tenantId);
    params.put("id", templateId);
    params.put("name", command.name());
    params.put("description", command.description());
    params.put("schema", command.draftSchemaJson());
    params.put("designer", command.draftDesignerJson());

    jdbc.update(
        """
        update work_record.wr_template
           set name = coalesce(:name, name),
               description = coalesce(:description, description),
               draft_schema_json = coalesce(cast(:schema as jsonb), draft_schema_json),
               draft_designer_json = coalesce(cast(:designer as jsonb), draft_designer_json),
               status = case when status = 'published' then 'draft' else status end
         where tenant_id = :tenantId and id = :id and deleted_at is null
        """,
        params);

    return find(tenantId, templateId).orElseThrow();
  }

  @Override
  public void updateCurrentVersion(String tenantId, String templateId, String versionId) {
    jdbc.update(
        """
        update work_record.wr_template
           set current_version_id = :versionId,
               status = 'published',
               enabled = true
         where tenant_id = :tenantId and id = :templateId and deleted_at is null
        """,
        Map.of("tenantId", tenantId, "templateId", templateId, "versionId", versionId));
  }

  @Override
  public void enable(String tenantId, String templateId) {
    jdbc.update(
        """
        update work_record.wr_template
           set enabled = true,
               status = case when current_version_id is null then 'draft' else 'published' end
         where tenant_id = :tenantId
           and id = :templateId
           and deleted_at is null
        """,
        Map.of("tenantId", tenantId, "templateId", templateId));
  }

  @Override
  public void disable(String tenantId, String templateId) {
    jdbc.update(
        """
        update work_record.wr_template
           set enabled = false,
               status = 'disabled'
         where tenant_id = :tenantId
           and id = :templateId
           and deleted_at is null
        """,
        Map.of("tenantId", tenantId, "templateId", templateId));
  }

  @Override
  public void archive(String tenantId, String templateId) {
    jdbc.update(
        """
        update work_record.wr_template
           set enabled = false,
               status = 'archived'
         where tenant_id = :tenantId
           and id = :templateId
           and deleted_at is null
        """,
        Map.of("tenantId", tenantId, "templateId", templateId));
  }

  private WorkRecordTemplate mapTemplate(ResultSet rs) throws SQLException {
    return new WorkRecordTemplate(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("code"),
        rs.getString("name"),
        rs.getString("description"),
        TemplateStatus.from(rs.getString("status")),
        rs.getBoolean("enabled"),
        rs.getString("current_version_id"),
        rs.getString("draft_schema_json"),
        rs.getString("draft_designer_json"),
        rs.getString("created_by"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class),
        rs.getObject("deleted_at", OffsetDateTime.class));
  }

  private Object nullable(Object value) {
    return value == null ? null : value;
  }

  private String blankJson(String value) {
    return value == null || value.isBlank() ? "{}" : value;
  }

  private String actorOrSystem(String actor) {
    return actor == null || actor.isBlank() ? "system" : actor;
  }
}

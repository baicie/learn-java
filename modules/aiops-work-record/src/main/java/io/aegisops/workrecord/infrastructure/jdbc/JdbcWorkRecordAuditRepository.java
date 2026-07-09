package io.aegisops.workrecord.infrastructure.jdbc;

import io.aegisops.common.id.Ids;
import io.aegisops.workrecord.application.port.WorkRecordAuditRepository;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkRecordAuditRepository implements WorkRecordAuditRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcWorkRecordAuditRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void record(
      String tenantId,
      String recordId,
      String templateId,
      String resourceType,
      String resourceId,
      String action,
      String actorId,
      String beforeJson,
      String afterJson,
      String detailJson) {
    Map<String, Object> params = new HashMap<>();
    params.put("id", Ids.newId());
    params.put("tenantId", tenantId);
    params.put("recordId", nullable(recordId));
    params.put("templateId", nullable(templateId));
    params.put("resourceType", resourceType);
    params.put("resourceId", resourceId);
    params.put("action", action);
    params.put("actorId", actorOrSystem(actorId));
    params.put("beforeJson", blankJson(beforeJson));
    params.put("afterJson", blankJson(afterJson));
    params.put("detailJson", blankJson(detailJson));
    jdbc.update(
        """
        insert into work_record.wr_record_audit_event(
          id, tenant_id, record_id, template_id, resource_type, resource_id,
          action, actor_id, before_json, after_json, detail_json)
        values (
          :id, :tenantId, :recordId, :templateId, :resourceType, :resourceId,
          :action, :actorId, :beforeJson::jsonb, :afterJson::jsonb, :detailJson::jsonb)
        """,
        params);
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

package io.aegisops.evidence;

import io.aegisops.common.exception.AppException;
import io.aegisops.common.id.Ids;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EvidenceCollectionTaskRepository {
  private final JdbcTemplate jdbc;

  public EvidenceCollectionTaskRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public String start(String tenantId, String incidentId, String collectorKey, String requestJson) {
    String id = Ids.newId();
    jdbc.update(
        """
            insert into evidence_collection_task(
              id, tenant_id, incident_id, collector_key, status, request_json, started_at
            ) values (?, ?, ?, ?, 'running', ?::jsonb, now())
            """,
        id,
        tenantId,
        incidentId,
        collectorKey,
        blankJson(requestJson));
    return id;
  }

  public void complete(String tenantId, String taskId, String resultJson) {
    int updated =
        jdbc.update(
            """
                update evidence_collection_task
                set status = 'completed',
                    result_json = ?::jsonb,
                    error_message = null,
                    finished_at = now()
                where tenant_id = ? and id = ?
                """,
            blankJson(resultJson),
            tenantId,
            taskId);
    ensureUpdated(updated, taskId);
  }

  public void fail(String tenantId, String taskId, String errorMessage) {
    int updated =
        jdbc.update(
            """
                update evidence_collection_task
                set status = 'failed',
                    error_message = ?,
                    finished_at = now()
                where tenant_id = ? and id = ?
                """,
            errorMessage,
            tenantId,
            taskId);
    ensureUpdated(updated, taskId);
  }

  public List<EvidenceCollectionTaskRecord> listByIncident(String tenantId, String incidentId) {
    return jdbc.query(
        """
            select id, tenant_id, incident_id, collector_key, status,
                   request_json::text, result_json::text, error_message,
                   started_at, finished_at, created_at
            from evidence_collection_task
            where tenant_id = ? and incident_id = ?
            order by created_at desc
            """,
        (rs, rowNum) ->
            new EvidenceCollectionTaskRecord(
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                rs.getString(7),
                rs.getString(8),
                rs.getObject(9, OffsetDateTime.class),
                rs.getObject(10, OffsetDateTime.class),
                rs.getObject(11, OffsetDateTime.class)),
        tenantId,
        incidentId);
  }

  private void ensureUpdated(int updated, String taskId) {
    if (updated != 1) {
      throw new AppException(
          "EVIDENCE_TASK_NOT_FOUND", "Evidence collection task not found: " + taskId);
    }
  }

  private String blankJson(String value) {
    return value == null || value.isBlank() ? "{}" : value;
  }
}

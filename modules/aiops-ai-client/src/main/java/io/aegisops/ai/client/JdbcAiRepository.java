package io.aegisops.ai.client;

import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.ai.client.dto.AiAlertRecord;
import io.aegisops.ai.client.dto.AiIncidentRecord;
import io.aegisops.ai.client.dto.AiRcaRecord;
import io.aegisops.ai.client.dto.SaveDiagnosisCommand;
import io.aegisops.ai.client.dto.TimelineCommand;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiRepository implements AiRepository {
  private final JdbcTemplate jdbc;

  public JdbcAiRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<AiIncidentRecord> findIncident(String tenantId, String incidentId) {
    try {
      return Optional.ofNullable(
          jdbc.queryForObject(
              """
                    select id, tenant_id, title, summary, severity, status, source, primary_asset_id,
                           aggregation_key, alert_count, suspected_root_cause, confidence,
                           started_at, detected_at, last_seen_at, created_at, updated_at
                    from incident
                    where tenant_id = ? and id = ?
                    """,
              (rs, rowNum) -> AiRows.incident(rs),
              tenantId,
              incidentId));
    } catch (EmptyResultDataAccessException ex) {
      return Optional.empty();
    }
  }

  @Override
  public List<AiAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
    return jdbc.query(
        """
                select a.id, a.source, a.source_event_id, a.severity, a.title, a.description,
                       a.asset_id, a.entity_type, a.entity_name, a.fingerprint,
                       a.labels::text as labels_json, a.starts_at
                from incident_event ie
                join incident i on i.id = ie.incident_id
                join alert_event a on a.id = ie.event_id
                where i.tenant_id = ?
                  and i.id = ?
                  and ie.event_type = 'alert'
                  and a.tenant_id = ?
                order by a.starts_at asc
                """,
        (rs, rowNum) ->
            new AiAlertRecord(
                rs.getString("id"),
                rs.getString("source"),
                rs.getString("source_event_id"),
                rs.getString("severity"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("asset_id"),
                rs.getString("entity_type"),
                rs.getString("entity_name"),
                rs.getString("fingerprint"),
                rs.getString("labels_json"),
                rs.getObject("starts_at", OffsetDateTime.class)),
        tenantId,
        incidentId,
        tenantId);
  }

  @Override
  public Optional<AiRcaRecord> findLatestRca(String tenantId, String incidentId) {
    try {
      return Optional.ofNullable(
          jdbc.queryForObject(
              """
                    select id, suspected_root_cause, confidence, summary,
                           evidence::text as evidence_json, model_version, created_at
                    from rca_analysis
                    where tenant_id = ? and incident_id = ?
                    order by created_at desc
                    limit 1
                    """,
              (rs, rowNum) ->
                  new AiRcaRecord(
                      rs.getString("id"),
                      rs.getString("suspected_root_cause"),
                      rs.getBigDecimal("confidence"),
                      rs.getString("summary"),
                      rs.getString("evidence_json"),
                      rs.getString("model_version"),
                      rs.getObject("created_at", OffsetDateTime.class)),
              tenantId,
              incidentId));
    } catch (EmptyResultDataAccessException ex) {
      return Optional.empty();
    }
  }

  @Override
  public Optional<io.aegisops.ai.client.dto.AiDiagnosisRecord> findLatestDiagnosis(
      String tenantId, String incidentId) {
    try {
      return Optional.ofNullable(
          jdbc.queryForObject(
              """
                    select id, tenant_id, incident_id, status, provider, model, agent_name,
                           summary, root_cause, impact,
                           next_steps::text as next_steps_json,
                           runbook_suggestions::text as runbook_suggestions_json,
                           risks::text as risks_json,
                           created_at
                    from ai_diagnosis
                    where tenant_id = ? and incident_id = ?
                    order by created_at desc
                    limit 1
                    """,
              (rs, rowNum) -> AiRows.diagnosis(rs),
              tenantId,
              incidentId));
    } catch (EmptyResultDataAccessException ex) {
      return Optional.empty();
    }
  }

  @Override
  public Optional<io.aegisops.ai.client.dto.AiDiagnosisRecord> findDiagnosis(
      String tenantId, String diagnosisId) {
    try {
      return Optional.ofNullable(
          jdbc.queryForObject(
              """
                    select id, tenant_id, incident_id, status, provider, model, agent_name,
                           summary, root_cause, impact,
                           next_steps::text as next_steps_json,
                           runbook_suggestions::text as runbook_suggestions_json,
                           risks::text as risks_json,
                           created_at
                    from ai_diagnosis
                    where tenant_id = ? and id = ?
                    """,
              (rs, rowNum) -> AiRows.diagnosis(rs),
              tenantId,
              diagnosisId));
    } catch (EmptyResultDataAccessException ex) {
      return Optional.empty();
    }
  }

  @Override
  public void saveDiagnosis(SaveDiagnosisCommand command) {
    AgentDiagnosisResponse response = command.response();
    jdbc.update(
        """
                insert into ai_diagnosis(
                  id, tenant_id, incident_id, status, provider, model, agent_name,
                  request_payload, response_raw, summary, root_cause, impact,
                  next_steps, runbook_suggestions, risks, created_at
                )
                values (?, ?, ?, 'completed', ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, now())
                """,
        command.id(),
        command.tenantId(),
        command.incidentId(),
        response.provider(),
        response.model(),
        response.agentName(),
        command.requestJson(),
        command.rawJson(),
        response.summary(),
        response.rootCause(),
        response.impact(),
        command.nextStepsJson(),
        command.runbookSuggestionsJson(),
        command.risksJson());
  }

  @Override
  public void addIncidentTimeline(TimelineCommand command) {
    jdbc.update(
        """
                insert into incident_timeline(id, incident_id, event_time, event_type, title, description, source, payload)
                values (?, ?, ?, 'ai_diagnosed', ?, ?, 'system', ?::jsonb)
                """,
        command.id(),
        command.incidentId(),
        command.eventTime(),
        command.title(),
        command.description(),
        command.payloadJson());
  }
}

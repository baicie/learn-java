package io.aegisops.report;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReportRepository implements ReportRepository {
  private final JdbcTemplate jdbc;

  public JdbcReportRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<ReportIncidentRecord> findIncident(String tenantId, String incidentId) {
    try {
      return Optional.ofNullable(
          jdbc.queryForObject(
              """
              select id, tenant_id, title, summary, severity, status, source,
                     primary_asset_id, aggregation_key, alert_count, impact_score,
                     suspected_root_cause, rca_confidence,
                     started_at, detected_at, last_seen_at, resolved_at, created_at, updated_at
              from incident
              where tenant_id = ? and id = ?
              """,
              (rs, rowNum) ->
                  new ReportIncidentRecord(
                      rs.getString("id"),
                      rs.getString("tenant_id"),
                      rs.getString("title"),
                      rs.getString("summary"),
                      rs.getString("severity"),
                      rs.getString("status"),
                      rs.getString("source"),
                      rs.getString("primary_asset_id"),
                      rs.getString("aggregation_key"),
                      rs.getInt("alert_count"),
                      rs.getBigDecimal("impact_score"),
                      rs.getString("suspected_root_cause"),
                      rs.getBigDecimal("rca_confidence"),
                      rs.getObject("started_at", OffsetDateTime.class),
                      rs.getObject("detected_at", OffsetDateTime.class),
                      rs.getObject("last_seen_at", OffsetDateTime.class),
                      rs.getObject("resolved_at", OffsetDateTime.class),
                      rs.getObject("created_at", OffsetDateTime.class),
                      rs.getObject("updated_at", OffsetDateTime.class)),
              tenantId,
              incidentId));
    } catch (EmptyResultDataAccessException ex) {
      return Optional.empty();
    }
  }

  @Override
  public List<ReportAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
    return jdbc.query(
        """
        select a.id, a.source, a.source_event_id, a.severity, a.title, a.description,
               a.asset_id, a.entity_type, a.entity_name, a.status, a.fingerprint,
               a.aggregation_key, a.labels::text as labels_json,
               a.starts_at, a.ends_at, a.created_at
        from incident_event ie
        join alert_event a on a.id = ie.event_id
        where ie.incident_id = ?
          and ie.event_type = 'alert'
          and a.tenant_id = ?
        order by a.starts_at asc, a.created_at asc
        """,
        (rs, rowNum) ->
            new ReportAlertRecord(
                rs.getString("id"),
                rs.getString("source"),
                rs.getString("source_event_id"),
                rs.getString("severity"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("asset_id"),
                rs.getString("entity_type"),
                rs.getString("entity_name"),
                rs.getString("status"),
                rs.getString("fingerprint"),
                rs.getString("aggregation_key"),
                rs.getString("labels_json"),
                rs.getObject("starts_at", OffsetDateTime.class),
                rs.getObject("ends_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class)),
        incidentId,
        tenantId);
  }

  @Override
  public List<ReportEvidenceRecord> listEvidence(String tenantId, String incidentId) {
    return jdbc.query(
        """
        select id, evidence_key, source, evidence_type, title, summary,
               time_range_start, time_range_end, confidence, payload_json::text as payload_json,
               created_at
        from diagnosis_evidence
        where tenant_id = ? and incident_id = ?
        order by created_at asc
        """,
        (rs, rowNum) ->
            new ReportEvidenceRecord(
                rs.getString("id"),
                rs.getString("evidence_key"),
                rs.getString("source"),
                rs.getString("evidence_type"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getObject("time_range_start", OffsetDateTime.class),
                rs.getObject("time_range_end", OffsetDateTime.class),
                rs.getBigDecimal("confidence"),
                rs.getString("payload_json"),
                rs.getObject("created_at", OffsetDateTime.class)),
        tenantId,
        incidentId);
  }

  @Override
  public Optional<ReportRcaRecord> findLatestRca(String tenantId, String incidentId) {
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
                  new ReportRcaRecord(
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
  public Optional<ReportAiDiagnosisRecord> findLatestAiDiagnosis(
      String tenantId, String incidentId) {
    try {
      return Optional.ofNullable(
          jdbc.queryForObject(
              """
              select id, provider, model, agent_name, summary, root_cause, impact,
                     next_steps::text as next_steps_json,
                     runbook_suggestions::text as runbook_suggestions_json,
                     risks::text as risks_json,
                     response_raw::text as response_raw_json,
                     created_at
              from ai_diagnosis
              where tenant_id = ? and incident_id = ?
              order by created_at desc
              limit 1
              """,
              (rs, rowNum) ->
                  new ReportAiDiagnosisRecord(
                      rs.getString("id"),
                      rs.getString("provider"),
                      rs.getString("model"),
                      rs.getString("agent_name"),
                      rs.getString("summary"),
                      rs.getString("root_cause"),
                      rs.getString("impact"),
                      rs.getString("next_steps_json"),
                      rs.getString("runbook_suggestions_json"),
                      rs.getString("risks_json"),
                      rs.getString("response_raw_json"),
                      rs.getObject("created_at", OffsetDateTime.class)),
              tenantId,
              incidentId));
    } catch (EmptyResultDataAccessException ex) {
      return Optional.empty();
    }
  }

  @Override
  public List<ReportTimelineRecord> listTimeline(String tenantId, String incidentId, int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 200));

    return jdbc.query(
        """
        select t.id, t.event_time, t.event_type, t.title, t.description, t.source,
               t.payload::text as payload_json
        from incident_timeline t
        join incident i on i.id = t.incident_id
        where i.tenant_id = ? and t.incident_id = ?
        order by t.event_time asc
        limit ?
        """,
        (rs, rowNum) ->
            new ReportTimelineRecord(
                rs.getString("id"),
                rs.getObject("event_time", OffsetDateTime.class),
                rs.getString("event_type"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("source"),
                rs.getString("payload_json")),
        tenantId,
        incidentId,
        safeLimit);
  }

  @Override
  public Optional<IncidentReportRecord> findLatestReport(String tenantId, String incidentId) {
    try {
      return Optional.ofNullable(
          jdbc.queryForObject(
              """
              select id, tenant_id, incident_id, version_no, report_type, format, title,
                     markdown_content, snapshot_json::text as snapshot_json, created_by,
                     created_at, updated_at
              from incident_report
              where tenant_id = ? and incident_id = ?
              order by created_at desc, version_no desc
              limit 1
              """,
              (rs, rowNum) -> toReport(rs),
              tenantId,
              incidentId));
    } catch (EmptyResultDataAccessException ex) {
      return Optional.empty();
    }
  }

  @Override
  public int nextVersionNo(String tenantId, String incidentId) {
    Integer next =
        jdbc.queryForObject(
            """
            select coalesce(max(version_no), 0) + 1
            from incident_report
            where tenant_id = ? and incident_id = ?
            """,
            Integer.class,
            tenantId,
            incidentId);

    return next == null ? 1 : next;
  }

  @Override
  public IncidentReportRecord insertReport(InsertReportParams params) {
    return jdbc.queryForObject(
        """
        insert into incident_report(
          id, tenant_id, incident_id, version_no, report_type, format,
          title, markdown_content, snapshot_json, created_by, created_at, updated_at
        )
        values (?, ?, ?, ?, 'incident_markdown', 'markdown', ?, ?, ?::jsonb, ?, now(), now())
        returning id, tenant_id, incident_id, version_no, report_type, format, title,
                  markdown_content, snapshot_json::text as snapshot_json, created_by,
                  created_at, updated_at
        """,
        (rs, rowNum) -> toReport(rs),
        params.id(),
        params.tenantId(),
        params.incidentId(),
        params.versionNo(),
        params.title(),
        params.markdownContent(),
        params.snapshotJson(),
        params.createdBy());
  }

  private IncidentReportRecord toReport(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new IncidentReportRecord(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("incident_id"),
        rs.getInt("version_no"),
        rs.getString("report_type"),
        rs.getString("format"),
        rs.getString("title"),
        rs.getString("markdown_content"),
        rs.getString("snapshot_json"),
        rs.getString("created_by"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }
}

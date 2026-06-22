package io.aegisops.evidence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Persistence helper for {@link ZabbixEvidenceCollectorService}. Owns the raw JDBC reads/writes so
 * the service stays free of parameter count violations and the 500-line file cap.
 */
@Component
class ZabbixEvidenceDao {
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  ZabbixEvidenceDao(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  JdbcTemplate jdbc() {
    return jdbc;
  }

  ObjectMapper objectMapper() {
    return objectMapper;
  }

  IncidentContext findIncident(String tenantId, String incidentId) {
    try {
      return jdbc.queryForObject(
          """
          select id, tenant_id, started_at, last_seen_at, resolved_at
          from incident
          where tenant_id = ? and id = ?
          """,
          (rs, rowNum) -> toIncidentContext(rs),
          tenantId,
          incidentId);
    } catch (EmptyResultDataAccessException ex) {
      throw new AppException("INCIDENT_NOT_FOUND", "Incident not found");
    }
  }

  List<AlertContext> linkedAlerts(String tenantId, String incidentId) {
    return jdbc.query(
        """
        select a.id, a.source, a.source_event_id, a.asset_id, a.entity_name,
               a.labels::text as labels_json, a.starts_at, a.ends_at
        from incident_event ie
        join alert_event a on a.id = ie.event_id
        where ie.incident_id = ?
          and ie.event_type = 'alert'
          and a.tenant_id = ?
        order by a.starts_at asc
        """,
        (rs, rowNum) -> toAlertContext(rs),
        incidentId,
        tenantId);
  }

  DataSourceRow findDatasource(String tenantId, String datasourceId) {
    try {
      return jdbc.queryForObject(
          """
          select id, config_json::text
          from datasource
          where tenant_id = ? and id = ? and type = 'zabbix'
          """,
          (rs, rowNum) -> new DataSourceRow(rs.getString("id"), rs.getString("config_json")),
          tenantId,
          datasourceId);
    } catch (EmptyResultDataAccessException ex) {
      throw new AppException("ZABBIX_DATASOURCE_NOT_FOUND", "Zabbix datasource not found");
    }
  }

  boolean upsertEvidence(
      String tenantId, String incidentId, String id, DiagnosisEvidenceDraft draft) {
    Boolean created =
        jdbc.queryForObject(
            """
            insert into diagnosis_evidence(
              id, tenant_id, incident_id, evidence_key, source, evidence_type, title, summary,
              time_range_start, time_range_end, confidence, payload_json, created_at, updated_at
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now(), now())
            on conflict (tenant_id, incident_id, evidence_key)
            do update set
              source = excluded.source,
              evidence_type = excluded.evidence_type,
              title = excluded.title,
              summary = excluded.summary,
              time_range_start = excluded.time_range_start,
              time_range_end = excluded.time_range_end,
              confidence = excluded.confidence,
              payload_json = excluded.payload_json,
              updated_at = now()
            returning (xmax = 0) as created
            """,
            Boolean.class,
            id,
            tenantId,
            incidentId,
            draft.evidenceKey(),
            draft.source(),
            draft.evidenceType(),
            draft.title(),
            draft.summary(),
            draft.timeRangeStart(),
            draft.timeRangeEnd(),
            draft.confidence(),
            writeJson(draft.payload()));

    return Boolean.TRUE.equals(created);
  }

  private IncidentContext toIncidentContext(ResultSet rs) throws SQLException {
    return new IncidentContext(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getObject("started_at", OffsetDateTime.class),
        rs.getObject("last_seen_at", OffsetDateTime.class),
        rs.getObject("resolved_at", OffsetDateTime.class));
  }

  private AlertContext toAlertContext(ResultSet rs) throws SQLException {
    return new AlertContext(
        rs.getString("id"),
        rs.getString("source"),
        rs.getString("source_event_id"),
        rs.getString("asset_id"),
        rs.getString("entity_name"),
        readMap(rs.getString("labels_json")),
        rs.getObject("starts_at", OffsetDateTime.class),
        rs.getObject("ends_at", OffsetDateTime.class));
  }

  private Map<String, Object> readMap(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }

    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception ex) {
      return Map.of();
    }
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (Exception ex) {
      throw new AppException("JSON_SERIALIZE_FAILED", "Failed to serialize evidence payload");
    }
  }

  record IncidentContext(
      String id,
      String tenantId,
      OffsetDateTime startedAt,
      OffsetDateTime lastSeenAt,
      OffsetDateTime resolvedAt) {}

  record AlertContext(
      String id,
      String source,
      String sourceEventId,
      String assetId,
      String entityName,
      Map<String, Object> labels,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt) {}

  record DataSourceRow(String id, String configJson) {}
}

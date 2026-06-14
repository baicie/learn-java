package io.aegisops.rca;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcRcaRepository implements RcaRepository {
    private final JdbcTemplate jdbc;

    public JdbcRcaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RcaIncidentRecord> findIncident(String tenantId, String incidentId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select id, tenant_id, title, summary, severity, status, source, primary_asset_id,
                           aggregation_key, alert_count, suspected_root_cause, confidence,
                           started_at, detected_at, last_seen_at, resolved_at, created_at, updated_at
                    from incident
                    where tenant_id = ? and id = ?
                    """, (rs, rowNum) -> RcaRows.incident(rs), tenantId, incidentId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<RcaAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
        return jdbc.query("""
                select a.id, a.source, a.source_event_id, a.severity, a.title, a.description,
                       a.asset_id, a.entity_type, a.entity_name, a.fingerprint, a.labels::text,
                       a.starts_at, a.created_at
                from incident_event ie
                join incident i on i.id = ie.incident_id
                join alert_event a on a.id = ie.event_id
                where i.tenant_id = ?
                  and i.id = ?
                  and ie.event_type = 'alert'
                  and a.tenant_id = ?
                order by a.starts_at asc
                """, (rs, rowNum) -> new RcaAlertRecord(
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
                rs.getString("labels"),
                rs.getObject("starts_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class)
        ), tenantId, incidentId, tenantId);
    }

    @Override
    public List<RcaAssetRelationRecord> listAssetRelations(String tenantId, List<String> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return List.of();
        }

        return jdbc.query("""
                select id, from_asset_id, to_asset_id, relation_type, confidence, source
                from asset_relation
                where tenant_id = ?
                  and (
                    from_asset_id = any(?)
                    or to_asset_id = any(?)
                  )
                order by confidence desc
                """, ps -> {
            ps.setString(1, tenantId);
            ps.setArray(2, ps.getConnection().createArrayOf("varchar", assetIds.toArray()));
            ps.setArray(3, ps.getConnection().createArrayOf("varchar", assetIds.toArray()));
        }, (rs, rowNum) -> new RcaAssetRelationRecord(
                rs.getString("id"),
                rs.getString("from_asset_id"),
                rs.getString("to_asset_id"),
                rs.getString("relation_type"),
                rs.getBigDecimal("confidence"),
                rs.getString("source")
        ));
    }

    @Override
    public Optional<RcaAnalysisRecord> findLatestAnalysis(String tenantId, String incidentId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select id, tenant_id, incident_id, status, suspected_root_cause, confidence,
                           summary, evidence::text, model_version, created_at
                    from rca_analysis
                    where tenant_id = ? and incident_id = ?
                    order by created_at desc
                    limit 1
                    """, (rs, rowNum) -> RcaRows.analysis(rs), tenantId, incidentId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void saveAnalysis(
            String id,
            String tenantId,
            String incidentId,
            String suspectedRootCause,
            BigDecimal confidence,
            String summary,
            String evidenceJson,
            String modelVersion
    ) {
        jdbc.update("""
                insert into rca_analysis(id, tenant_id, incident_id, status, suspected_root_cause,
                                         confidence, summary, evidence, model_version, created_at)
                values (?, ?, ?, 'completed', ?, ?, ?, ?::jsonb, ?, now())
                """,
                id,
                tenantId,
                incidentId,
                suspectedRootCause,
                confidence,
                summary,
                evidenceJson,
                modelVersion
        );
    }

    @Override
    public Optional<RcaAnalysisRecord> findAnalysis(String tenantId, String id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select id, tenant_id, incident_id, status, suspected_root_cause, confidence,
                           summary, evidence::text, model_version, created_at
                    from rca_analysis
                    where tenant_id = ? and id = ?
                    """, (rs, rowNum) -> RcaRows.analysis(rs), tenantId, id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void updateIncidentRca(String tenantId, String incidentId, String suspectedRootCause, BigDecimal confidence) {
        jdbc.update("""
                update incident
                set suspected_root_cause = ?,
                    confidence = ?,
                    updated_at = now()
                where tenant_id = ? and id = ?
                """, suspectedRootCause, confidence, tenantId, incidentId);
    }

    @Override
    public void addIncidentTimeline(
            String id,
            String incidentId,
            OffsetDateTime eventTime,
            String title,
            String description,
            String payloadJson
    ) {
        jdbc.update("""
                insert into incident_timeline(id, incident_id, event_time, event_type, title, description, source, payload)
                values (?, ?, ?, 'rca_analyzed', ?, ?, 'system', ?::jsonb)
                """, id, incidentId, eventTime, title, description, payloadJson);
    }
}

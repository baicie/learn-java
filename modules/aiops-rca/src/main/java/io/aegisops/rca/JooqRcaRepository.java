package io.aegisops.rca;

import static io.aegisops.persistence.AegisJooq.jsonArrayOrEmpty;
import static io.aegisops.persistence.AegisJooq.jsonbArrayValue;
import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.public_.Tables.ALERT_EVENT;
import static io.aegisops.persistence.jooq.public_.Tables.ASSET_RELATION;
import static io.aegisops.persistence.jooq.public_.Tables.INCIDENT;
import static io.aegisops.persistence.jooq.public_.Tables.INCIDENT_EVENT;
import static io.aegisops.persistence.jooq.public_.Tables.INCIDENT_TIMELINE;
import static io.aegisops.persistence.jooq.public_.Tables.RCA_ANALYSIS;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** jOOQ generated Tables based RCA repository. */
@Repository
public class JooqRcaRepository implements RcaRepository {
  private final DSLContext dsl;
  private final JdbcTemplate jdbc;

  public JooqRcaRepository(DSLContext dsl, JdbcTemplate jdbc) {
    this.dsl = dsl;
    this.jdbc = jdbc;
  }

  @Override
  public Optional<RcaIncidentRecord> findIncident(String tenantId, String incidentId) {
    return dsl.select(
            INCIDENT.ID,
            INCIDENT.TENANT_ID,
            INCIDENT.TITLE,
            INCIDENT.SUMMARY,
            INCIDENT.SEVERITY,
            INCIDENT.STATUS,
            INCIDENT.SOURCE,
            INCIDENT.PRIMARY_ASSET_ID,
            INCIDENT.AGGREGATION_KEY,
            INCIDENT.ALERT_COUNT,
            INCIDENT.SUSPECTED_ROOT_CAUSE,
            INCIDENT.CONFIDENCE,
            INCIDENT.STARTED_AT,
            INCIDENT.DETECTED_AT,
            INCIDENT.LAST_SEEN_AT,
            INCIDENT.RESOLVED_AT,
            INCIDENT.CREATED_AT,
            INCIDENT.UPDATED_AT)
        .from(INCIDENT)
        .where(INCIDENT.TENANT_ID.eq(tenantId))
        .and(INCIDENT.ID.eq(incidentId))
        .fetchOptional(JooqRcaRepository::toIncidentRecord);
  }

  @Override
  public List<RcaAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
    return dsl.select(
            ALERT_EVENT.ID,
            ALERT_EVENT.SOURCE,
            ALERT_EVENT.SOURCE_EVENT_ID,
            ALERT_EVENT.SEVERITY,
            ALERT_EVENT.TITLE,
            ALERT_EVENT.DESCRIPTION,
            ALERT_EVENT.ASSET_ID,
            ALERT_EVENT.ENTITY_TYPE,
            ALERT_EVENT.ENTITY_NAME,
            ALERT_EVENT.FINGERPRINT,
            ALERT_EVENT.LABELS.cast(String.class).as("labels"),
            ALERT_EVENT.STARTS_AT,
            ALERT_EVENT.CREATED_AT)
        .from(INCIDENT_EVENT)
        .join(INCIDENT)
        .on(INCIDENT.ID.eq(INCIDENT_EVENT.INCIDENT_ID))
        .join(ALERT_EVENT)
        .on(ALERT_EVENT.ID.eq(INCIDENT_EVENT.EVENT_ID))
        .where(INCIDENT.TENANT_ID.eq(tenantId))
        .and(INCIDENT.ID.eq(incidentId))
        .and(INCIDENT_EVENT.EVENT_TYPE.eq("alert"))
        .and(ALERT_EVENT.TENANT_ID.eq(tenantId))
        .orderBy(ALERT_EVENT.STARTS_AT.asc())
        .fetch(JooqRcaRepository::toAlertRecord);
  }

  @Override
  public List<RcaAssetRelationRecord> listAssetRelations(String tenantId, List<String> assetIds) {
    if (assetIds == null || assetIds.isEmpty()) {
      return List.of();
    }

    return dsl.select(
            ASSET_RELATION.ID,
            ASSET_RELATION.FROM_ASSET_ID,
            ASSET_RELATION.TO_ASSET_ID,
            ASSET_RELATION.RELATION_TYPE,
            ASSET_RELATION.CONFIDENCE,
            ASSET_RELATION.SOURCE)
        .from(ASSET_RELATION)
        .where(ASSET_RELATION.TENANT_ID.eq(tenantId))
        .and(ASSET_RELATION.FROM_ASSET_ID.in(assetIds).or(ASSET_RELATION.TO_ASSET_ID.in(assetIds)))
        .orderBy(ASSET_RELATION.CONFIDENCE.desc())
        .fetch(JooqRcaRepository::toAssetRelationRecord);
  }

  @Override
  public List<RcaDiagnosisEvidenceRecord> listDiagnosisEvidence(
      String tenantId, String incidentId) {
    return jdbc.query(
        """
        select id, incident_id, evidence_key, source, evidence_type, title, summary,
               time_range_start, time_range_end, confidence, payload_json::text as payload_json
        from diagnosis_evidence
        where tenant_id = ? and incident_id = ?
        order by created_at asc
        """,
        this::toDiagnosisEvidenceRecord,
        tenantId,
        incidentId);
  }

  @Override
  public Optional<RcaAnalysisRecord> findLatestAnalysis(String tenantId, String incidentId) {
    return findAnalysisByCondition(
        RCA_ANALYSIS.TENANT_ID.eq(tenantId).and(RCA_ANALYSIS.INCIDENT_ID.eq(incidentId)), true);
  }

  @Override
  public void saveAnalysis(SaveAnalysisParams params) {
    dsl.insertInto(RCA_ANALYSIS)
        .set(RCA_ANALYSIS.ID, params.id())
        .set(RCA_ANALYSIS.TENANT_ID, params.tenantId())
        .set(RCA_ANALYSIS.INCIDENT_ID, params.incidentId())
        .set(RCA_ANALYSIS.STATUS, "completed")
        .set(RCA_ANALYSIS.SUSPECTED_ROOT_CAUSE, params.suspectedRootCause())
        .set(RCA_ANALYSIS.CONFIDENCE, params.confidence())
        .set(RCA_ANALYSIS.SUMMARY, params.summary())
        .set(RCA_ANALYSIS.EVIDENCE, jsonbArrayValue(jsonArrayOrEmpty(params.evidenceJson())))
        .set(RCA_ANALYSIS.MODEL_VERSION, params.modelVersion())
        .set(RCA_ANALYSIS.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<RcaAnalysisRecord> findAnalysis(String tenantId, String id) {
    return findAnalysisByCondition(
        RCA_ANALYSIS.TENANT_ID.eq(tenantId).and(RCA_ANALYSIS.ID.eq(id)), false);
  }

  @Override
  public void updateIncidentRca(
      String tenantId, String incidentId, String suspectedRootCause, BigDecimal confidence) {
    dsl.update(INCIDENT)
        .set(INCIDENT.SUSPECTED_ROOT_CAUSE, suspectedRootCause)
        .set(INCIDENT.CONFIDENCE, confidence)
        .set(INCIDENT.UPDATED_AT, DSL.currentOffsetDateTime())
        .where(INCIDENT.TENANT_ID.eq(tenantId))
        .and(INCIDENT.ID.eq(incidentId))
        .execute();
  }

  @Override
  public void addIncidentTimeline(AddTimelineParams params) {
    dsl.insertInto(INCIDENT_TIMELINE)
        .set(INCIDENT_TIMELINE.ID, params.id())
        .set(INCIDENT_TIMELINE.INCIDENT_ID, params.incidentId())
        .set(INCIDENT_TIMELINE.EVENT_TIME, params.eventTime())
        .set(INCIDENT_TIMELINE.EVENT_TYPE, "rca_analyzed")
        .set(INCIDENT_TIMELINE.TITLE, params.title())
        .set(INCIDENT_TIMELINE.DESCRIPTION, params.description())
        .set(INCIDENT_TIMELINE.SOURCE, "system")
        .set(INCIDENT_TIMELINE.PAYLOAD, jsonbValue(params.payloadJson()))
        .execute();
  }

  private Optional<RcaAnalysisRecord> findAnalysisByCondition(Condition condition, boolean latest) {
    var query =
        dsl.select(
                RCA_ANALYSIS.ID,
                RCA_ANALYSIS.TENANT_ID,
                RCA_ANALYSIS.INCIDENT_ID,
                RCA_ANALYSIS.STATUS,
                RCA_ANALYSIS.SUSPECTED_ROOT_CAUSE,
                RCA_ANALYSIS.CONFIDENCE,
                RCA_ANALYSIS.SUMMARY,
                RCA_ANALYSIS.EVIDENCE.cast(String.class).as("evidence"),
                RCA_ANALYSIS.MODEL_VERSION,
                RCA_ANALYSIS.CREATED_AT)
            .from(RCA_ANALYSIS)
            .where(condition)
            .orderBy(latest ? RCA_ANALYSIS.CREATED_AT.desc() : RCA_ANALYSIS.ID.asc())
            .limit(1);

    return query.fetchOptional(JooqRcaRepository::toAnalysisRecord);
  }

  private RcaDiagnosisEvidenceRecord toDiagnosisEvidenceRecord(ResultSet rs, int rowNum)
      throws SQLException {
    return new RcaDiagnosisEvidenceRecord(
        rs.getString("id"),
        rs.getString("incident_id"),
        rs.getString("evidence_key"),
        rs.getString("source"),
        rs.getString("evidence_type"),
        rs.getString("title"),
        rs.getString("summary"),
        toOffsetDateTime(rs.getObject("time_range_start")),
        toOffsetDateTime(rs.getObject("time_range_end")),
        rs.getBigDecimal("confidence"),
        rs.getString("payload_json"));
  }

  private static OffsetDateTime toOffsetDateTime(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof OffsetDateTime odt) {
      return odt;
    }
    return null;
  }

  private static RcaIncidentRecord toIncidentRecord(org.jooq.Record record) {
    return new RcaIncidentRecord(
        record.get(INCIDENT.ID),
        record.get(INCIDENT.TENANT_ID),
        record.get(INCIDENT.TITLE),
        record.get(INCIDENT.SUMMARY),
        record.get(INCIDENT.SEVERITY),
        record.get(INCIDENT.STATUS),
        record.get(INCIDENT.SOURCE),
        record.get(INCIDENT.PRIMARY_ASSET_ID),
        record.get(INCIDENT.AGGREGATION_KEY),
        value(record.get(INCIDENT.ALERT_COUNT)),
        record.get(INCIDENT.SUSPECTED_ROOT_CAUSE),
        record.get(INCIDENT.CONFIDENCE),
        record.get(INCIDENT.STARTED_AT),
        record.get(INCIDENT.DETECTED_AT),
        record.get(INCIDENT.LAST_SEEN_AT),
        record.get(INCIDENT.RESOLVED_AT),
        record.get(INCIDENT.CREATED_AT),
        record.get(INCIDENT.UPDATED_AT));
  }

  private static RcaAlertRecord toAlertRecord(org.jooq.Record record) {
    return new RcaAlertRecord(
        record.get(ALERT_EVENT.ID),
        record.get(ALERT_EVENT.SOURCE),
        record.get(ALERT_EVENT.SOURCE_EVENT_ID),
        record.get(ALERT_EVENT.SEVERITY),
        record.get(ALERT_EVENT.TITLE),
        record.get(ALERT_EVENT.DESCRIPTION),
        record.get(ALERT_EVENT.ASSET_ID),
        record.get(ALERT_EVENT.ENTITY_TYPE),
        record.get(ALERT_EVENT.ENTITY_NAME),
        record.get(ALERT_EVENT.FINGERPRINT),
        record.get("labels", String.class),
        record.get(ALERT_EVENT.STARTS_AT),
        record.get(ALERT_EVENT.CREATED_AT));
  }

  private static RcaAssetRelationRecord toAssetRelationRecord(org.jooq.Record record) {
    return new RcaAssetRelationRecord(
        record.get(ASSET_RELATION.ID),
        record.get(ASSET_RELATION.FROM_ASSET_ID),
        record.get(ASSET_RELATION.TO_ASSET_ID),
        record.get(ASSET_RELATION.RELATION_TYPE),
        record.get(ASSET_RELATION.CONFIDENCE),
        record.get(ASSET_RELATION.SOURCE));
  }

  private static RcaAnalysisRecord toAnalysisRecord(org.jooq.Record record) {
    return new RcaAnalysisRecord(
        record.get(RCA_ANALYSIS.ID),
        record.get(RCA_ANALYSIS.TENANT_ID),
        record.get(RCA_ANALYSIS.INCIDENT_ID),
        record.get(RCA_ANALYSIS.STATUS),
        record.get(RCA_ANALYSIS.SUSPECTED_ROOT_CAUSE),
        record.get(RCA_ANALYSIS.CONFIDENCE),
        record.get(RCA_ANALYSIS.SUMMARY),
        record.get("evidence", String.class),
        record.get(RCA_ANALYSIS.MODEL_VERSION),
        record.get(RCA_ANALYSIS.CREATED_AT));
  }

  private static int value(Integer value) {
    return value == null ? 0 : value;
  }
}

package io.aegisops.rca;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.AegisTables.ALERT_EVENT;
import static io.aegisops.persistence.AegisTables.ASSET_RELATION;
import static io.aegisops.persistence.AegisTables.INCIDENT;
import static io.aegisops.persistence.AegisTables.INCIDENT_EVENT;
import static io.aegisops.persistence.AegisTables.INCIDENT_TIMELINE;
import static io.aegisops.persistence.AegisTables.RCA_ANALYSIS;
import static io.aegisops.persistence.AegisTables.decimal;
import static io.aegisops.persistence.AegisTables.integer;
import static io.aegisops.persistence.AegisTables.jsonb;
import static io.aegisops.persistence.AegisTables.str;
import static io.aegisops.persistence.AegisTables.time;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** jOOQ based RCA repository. */
@Repository
public class JdbcRcaRepository implements RcaRepository {
  private final DSLContext dsl;

  public JdbcRcaRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Optional<RcaIncidentRecord> findIncident(String tenantId, String incidentId) {
    return dsl.select(
            str(INCIDENT, "id"),
            str(INCIDENT, "tenant_id"),
            str(INCIDENT, "title"),
            str(INCIDENT, "summary"),
            str(INCIDENT, "severity"),
            str(INCIDENT, "status"),
            str(INCIDENT, "source"),
            str(INCIDENT, "primary_asset_id"),
            str(INCIDENT, "aggregation_key"),
            integer(INCIDENT, "alert_count"),
            str(INCIDENT, "suspected_root_cause"),
            decimal(INCIDENT, "confidence"),
            time(INCIDENT, "started_at"),
            time(INCIDENT, "detected_at"),
            time(INCIDENT, "last_seen_at"),
            time(INCIDENT, "resolved_at"),
            time(INCIDENT, "created_at"),
            time(INCIDENT, "updated_at"))
        .from(INCIDENT)
        .where(str(INCIDENT, "tenant_id").eq(tenantId))
        .and(str(INCIDENT, "id").eq(incidentId))
        .fetchOptional(JdbcRcaRepository::toIncidentRecord);
  }

  @Override
  public List<RcaAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
    return dsl.select(
            str(ALERT_EVENT, "id"),
            str(ALERT_EVENT, "source"),
            str(ALERT_EVENT, "source_event_id"),
            str(ALERT_EVENT, "severity"),
            str(ALERT_EVENT, "title"),
            str(ALERT_EVENT, "description"),
            str(ALERT_EVENT, "asset_id"),
            str(ALERT_EVENT, "entity_type"),
            str(ALERT_EVENT, "entity_name"),
            str(ALERT_EVENT, "fingerprint"),
            jsonb(ALERT_EVENT, "labels").cast(String.class).as("labels"),
            time(ALERT_EVENT, "starts_at"),
            time(ALERT_EVENT, "created_at"))
        .from(INCIDENT_EVENT)
        .join(INCIDENT)
        .on(str(INCIDENT, "id").eq(str(INCIDENT_EVENT, "incident_id")))
        .join(ALERT_EVENT)
        .on(str(ALERT_EVENT, "id").eq(str(INCIDENT_EVENT, "event_id")))
        .where(str(INCIDENT, "tenant_id").eq(tenantId))
        .and(str(INCIDENT, "id").eq(incidentId))
        .and(str(INCIDENT_EVENT, "event_type").eq("alert"))
        .and(str(ALERT_EVENT, "tenant_id").eq(tenantId))
        .orderBy(time(ALERT_EVENT, "starts_at").asc())
        .fetch(JdbcRcaRepository::toAlertRecord);
  }

  @Override
  public List<RcaAssetRelationRecord> listAssetRelations(String tenantId, List<String> assetIds) {
    if (assetIds == null || assetIds.isEmpty()) {
      return List.of();
    }

    return dsl.select(
            str(ASSET_RELATION, "id"),
            str(ASSET_RELATION, "from_asset_id"),
            str(ASSET_RELATION, "to_asset_id"),
            str(ASSET_RELATION, "relation_type"),
            decimal(ASSET_RELATION, "confidence"),
            str(ASSET_RELATION, "source"))
        .from(ASSET_RELATION)
        .where(str(ASSET_RELATION, "tenant_id").eq(tenantId))
        .and(
            str(ASSET_RELATION, "from_asset_id")
                .in(assetIds)
                .or(str(ASSET_RELATION, "to_asset_id").in(assetIds)))
        .orderBy(decimal(ASSET_RELATION, "confidence").desc())
        .fetch(JdbcRcaRepository::toAssetRelationRecord);
  }

  @Override
  public Optional<RcaAnalysisRecord> findLatestAnalysis(String tenantId, String incidentId) {
    return findAnalysisByCondition(
        str(RCA_ANALYSIS, "tenant_id")
            .eq(tenantId)
            .and(str(RCA_ANALYSIS, "incident_id").eq(incidentId)),
        true);
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
      String modelVersion) {
    dsl.insertInto(RCA_ANALYSIS)
        .set(str(RCA_ANALYSIS, "id"), id)
        .set(str(RCA_ANALYSIS, "tenant_id"), tenantId)
        .set(str(RCA_ANALYSIS, "incident_id"), incidentId)
        .set(str(RCA_ANALYSIS, "status"), "completed")
        .set(str(RCA_ANALYSIS, "suspected_root_cause"), suspectedRootCause)
        .set(decimal(RCA_ANALYSIS, "confidence"), confidence)
        .set(str(RCA_ANALYSIS, "summary"), summary)
        .set(jsonb(RCA_ANALYSIS, "evidence"), jsonbValue(evidenceJson))
        .set(str(RCA_ANALYSIS, "model_version"), modelVersion)
        .set(time(RCA_ANALYSIS, "created_at"), DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<RcaAnalysisRecord> findAnalysis(String tenantId, String id) {
    return findAnalysisByCondition(
        str(RCA_ANALYSIS, "tenant_id").eq(tenantId).and(str(RCA_ANALYSIS, "id").eq(id)), false);
  }

  @Override
  public void updateIncidentRca(
      String tenantId, String incidentId, String suspectedRootCause, BigDecimal confidence) {
    dsl.update(INCIDENT)
        .set(str(INCIDENT, "suspected_root_cause"), suspectedRootCause)
        .set(decimal(INCIDENT, "confidence"), confidence)
        .set(time(INCIDENT, "updated_at"), DSL.currentOffsetDateTime())
        .where(str(INCIDENT, "tenant_id").eq(tenantId))
        .and(str(INCIDENT, "id").eq(incidentId))
        .execute();
  }

  @Override
  public void addIncidentTimeline(
      String id,
      String incidentId,
      OffsetDateTime eventTime,
      String title,
      String description,
      String payloadJson) {
    dsl.insertInto(INCIDENT_TIMELINE)
        .set(str(INCIDENT_TIMELINE, "id"), id)
        .set(str(INCIDENT_TIMELINE, "incident_id"), incidentId)
        .set(time(INCIDENT_TIMELINE, "event_time"), eventTime)
        .set(str(INCIDENT_TIMELINE, "event_type"), "rca_analyzed")
        .set(str(INCIDENT_TIMELINE, "title"), title)
        .set(str(INCIDENT_TIMELINE, "description"), description)
        .set(str(INCIDENT_TIMELINE, "source"), "system")
        .set(jsonb(INCIDENT_TIMELINE, "payload"), jsonbValue(payloadJson))
        .execute();
  }

  private Optional<RcaAnalysisRecord> findAnalysisByCondition(Condition condition, boolean latest) {
    var query =
        dsl.select(
                str(RCA_ANALYSIS, "id"),
                str(RCA_ANALYSIS, "tenant_id"),
                str(RCA_ANALYSIS, "incident_id"),
                str(RCA_ANALYSIS, "status"),
                str(RCA_ANALYSIS, "suspected_root_cause"),
                decimal(RCA_ANALYSIS, "confidence"),
                str(RCA_ANALYSIS, "summary"),
                jsonb(RCA_ANALYSIS, "evidence").cast(String.class).as("evidence"),
                str(RCA_ANALYSIS, "model_version"),
                time(RCA_ANALYSIS, "created_at"))
            .from(RCA_ANALYSIS)
            .where(condition)
            .orderBy(
                latest ? time(RCA_ANALYSIS, "created_at").desc() : str(RCA_ANALYSIS, "id").asc())
            .limit(1);

    return query.fetchOptional(JdbcRcaRepository::toAnalysisRecord);
  }

  private static RcaIncidentRecord toIncidentRecord(org.jooq.Record record) {
    return new RcaIncidentRecord(
        record.get(str(INCIDENT, "id")),
        record.get(str(INCIDENT, "tenant_id")),
        record.get(str(INCIDENT, "title")),
        record.get(str(INCIDENT, "summary")),
        record.get(str(INCIDENT, "severity")),
        record.get(str(INCIDENT, "status")),
        record.get(str(INCIDENT, "source")),
        record.get(str(INCIDENT, "primary_asset_id")),
        record.get(str(INCIDENT, "aggregation_key")),
        value(record.get(integer(INCIDENT, "alert_count"))),
        record.get(str(INCIDENT, "suspected_root_cause")),
        record.get(decimal(INCIDENT, "confidence")),
        record.get(time(INCIDENT, "started_at")),
        record.get(time(INCIDENT, "detected_at")),
        record.get(time(INCIDENT, "last_seen_at")),
        record.get(time(INCIDENT, "resolved_at")),
        record.get(time(INCIDENT, "created_at")),
        record.get(time(INCIDENT, "updated_at")));
  }

  private static RcaAlertRecord toAlertRecord(org.jooq.Record record) {
    return new RcaAlertRecord(
        record.get(str(ALERT_EVENT, "id")),
        record.get(str(ALERT_EVENT, "source")),
        record.get(str(ALERT_EVENT, "source_event_id")),
        record.get(str(ALERT_EVENT, "severity")),
        record.get(str(ALERT_EVENT, "title")),
        record.get(str(ALERT_EVENT, "description")),
        record.get(str(ALERT_EVENT, "asset_id")),
        record.get(str(ALERT_EVENT, "entity_type")),
        record.get(str(ALERT_EVENT, "entity_name")),
        record.get(str(ALERT_EVENT, "fingerprint")),
        record.get("labels", String.class),
        record.get(time(ALERT_EVENT, "starts_at")),
        record.get(time(ALERT_EVENT, "created_at")));
  }

  private static RcaAssetRelationRecord toAssetRelationRecord(org.jooq.Record record) {
    return new RcaAssetRelationRecord(
        record.get(str(ASSET_RELATION, "id")),
        record.get(str(ASSET_RELATION, "from_asset_id")),
        record.get(str(ASSET_RELATION, "to_asset_id")),
        record.get(str(ASSET_RELATION, "relation_type")),
        record.get(decimal(ASSET_RELATION, "confidence")),
        record.get(str(ASSET_RELATION, "source")));
  }

  private static RcaAnalysisRecord toAnalysisRecord(org.jooq.Record record) {
    return new RcaAnalysisRecord(
        record.get(str(RCA_ANALYSIS, "id")),
        record.get(str(RCA_ANALYSIS, "tenant_id")),
        record.get(str(RCA_ANALYSIS, "incident_id")),
        record.get(str(RCA_ANALYSIS, "status")),
        record.get(str(RCA_ANALYSIS, "suspected_root_cause")),
        record.get(decimal(RCA_ANALYSIS, "confidence")),
        record.get(str(RCA_ANALYSIS, "summary")),
        record.get("evidence", String.class),
        record.get(str(RCA_ANALYSIS, "model_version")),
        record.get(time(RCA_ANALYSIS, "created_at")));
  }

  private static int value(Integer value) {
    return value == null ? 0 : value;
  }
}

package io.aegisops.execution;

import static io.aegisops.persistence.jooq.Tables.AI_DIAGNOSIS;
import static io.aegisops.persistence.jooq.Tables.EXECUTION_RUN;
import static io.aegisops.persistence.jooq.Tables.INCIDENT;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_TIMELINE;
import static io.aegisops.persistence.jooq.Tables.RCA_ANALYSIS;
import static io.aegisops.persistence.jooq.Tables.ROLLBACK_PLAN;

import io.aegisops.execution.dto.PostmortemSourceBundle;
import io.aegisops.execution.dto.PostmortemSourceBundle.AiDiagnosisSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.ExecutionSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.IncidentSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.RcaSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.RollbackSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.TimelineSnapshot;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqPostmortemSourceRepository implements PostmortemSourceRepository {
  private final DSLContext dsl;

  public JooqPostmortemSourceRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Optional<PostmortemSourceBundle> load(String tenantId, String incidentId) {
    Optional<IncidentSnapshot> incident = loadIncident(tenantId, incidentId);
    if (incident.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
        new PostmortemSourceBundle(
            incident.get(),
            loadRca(tenantId, incidentId),
            loadAiDiagnosis(tenantId, incidentId),
            loadExecutions(tenantId, incidentId),
            loadRollbackPlans(tenantId, incidentId),
            loadTimeline(tenantId, incidentId)));
  }

  private Optional<IncidentSnapshot> loadIncident(String tenantId, String incidentId) {
    return dsl.select(
            INCIDENT.ID,
            INCIDENT.TITLE,
            INCIDENT.STATUS,
            INCIDENT.SEVERITY,
            INCIDENT.CREATED_AT,
            INCIDENT.UPDATED_AT)
        .from(INCIDENT)
        .where(INCIDENT.TENANT_ID.eq(tenantId))
        .and(INCIDENT.ID.eq(incidentId))
        .fetchOptional(
            record ->
                new IncidentSnapshot(
                    record.get(INCIDENT.ID),
                    record.get(INCIDENT.TITLE),
                    record.get(INCIDENT.STATUS),
                    record.get(INCIDENT.SEVERITY),
                    record.get(INCIDENT.CREATED_AT),
                    record.get(INCIDENT.UPDATED_AT)));
  }

  private List<RcaSnapshot> loadRca(String tenantId, String incidentId) {
    return dsl.select(
            RCA_ANALYSIS.ID,
            RCA_ANALYSIS.SUMMARY,
            RCA_ANALYSIS.SUSPECTED_ROOT_CAUSE,
            RCA_ANALYSIS.CONFIDENCE,
            RCA_ANALYSIS.CREATED_AT)
        .from(RCA_ANALYSIS)
        .where(RCA_ANALYSIS.TENANT_ID.eq(tenantId))
        .and(RCA_ANALYSIS.INCIDENT_ID.eq(incidentId))
        .orderBy(RCA_ANALYSIS.CREATED_AT.desc())
        .fetch(
            record ->
                new RcaSnapshot(
                    record.get(RCA_ANALYSIS.ID),
                    record.get(RCA_ANALYSIS.SUMMARY),
                    record.get(RCA_ANALYSIS.SUSPECTED_ROOT_CAUSE),
                    String.valueOf(record.get(RCA_ANALYSIS.CONFIDENCE)),
                    record.get(RCA_ANALYSIS.CREATED_AT)));
  }

  private List<AiDiagnosisSnapshot> loadAiDiagnosis(String tenantId, String incidentId) {
    return dsl.select(
            AI_DIAGNOSIS.ID,
            AI_DIAGNOSIS.SUMMARY,
            AI_DIAGNOSIS.ROOT_CAUSE,
            AI_DIAGNOSIS.NEXT_STEPS,
            AI_DIAGNOSIS.CREATED_AT)
        .from(AI_DIAGNOSIS)
        .where(AI_DIAGNOSIS.TENANT_ID.eq(tenantId))
        .and(AI_DIAGNOSIS.INCIDENT_ID.eq(incidentId))
        .orderBy(AI_DIAGNOSIS.CREATED_AT.desc())
        .fetch(
            record ->
                new AiDiagnosisSnapshot(
                    record.get(AI_DIAGNOSIS.ID),
                    record.get(AI_DIAGNOSIS.SUMMARY),
                    record.get(AI_DIAGNOSIS.ROOT_CAUSE),
                    record.get(AI_DIAGNOSIS.NEXT_STEPS) != null
                        ? record.get(AI_DIAGNOSIS.NEXT_STEPS).toString()
                        : null,
                    record.get(AI_DIAGNOSIS.CREATED_AT)));
  }

  private List<ExecutionSnapshot> loadExecutions(String tenantId, String incidentId) {
    return dsl.select(
            EXECUTION_RUN.ID,
            EXECUTION_RUN.MODE,
            EXECUTION_RUN.EXECUTION_KIND,
            EXECUTION_RUN.STATUS,
            EXECUTION_RUN.SUMMARY,
            EXECUTION_RUN.STARTED_AT,
            EXECUTION_RUN.FINISHED_AT)
        .from(EXECUTION_RUN)
        .where(EXECUTION_RUN.TENANT_ID.eq(tenantId))
        .and(EXECUTION_RUN.INCIDENT_ID.eq(incidentId))
        .orderBy(EXECUTION_RUN.CREATED_AT.asc())
        .fetch(
            record ->
                new ExecutionSnapshot(
                    record.get(EXECUTION_RUN.ID),
                    record.get(EXECUTION_RUN.MODE),
                    record.get(EXECUTION_RUN.EXECUTION_KIND),
                    record.get(EXECUTION_RUN.STATUS),
                    record.get(EXECUTION_RUN.SUMMARY),
                    record.get(EXECUTION_RUN.STARTED_AT),
                    record.get(EXECUTION_RUN.FINISHED_AT)));
  }

  private List<RollbackSnapshot> loadRollbackPlans(String tenantId, String incidentId) {
    return dsl.select(
            ROLLBACK_PLAN.ID,
            ROLLBACK_PLAN.STATUS,
            ROLLBACK_PLAN.REASON,
            ROLLBACK_PLAN.CREATED_AT,
            ROLLBACK_PLAN.UPDATED_AT)
        .from(ROLLBACK_PLAN)
        .where(ROLLBACK_PLAN.TENANT_ID.eq(tenantId))
        .and(ROLLBACK_PLAN.INCIDENT_ID.eq(incidentId))
        .orderBy(ROLLBACK_PLAN.CREATED_AT.asc())
        .fetch(
            record ->
                new RollbackSnapshot(
                    record.get(ROLLBACK_PLAN.ID),
                    record.get(ROLLBACK_PLAN.STATUS),
                    record.get(ROLLBACK_PLAN.REASON),
                    record.get(ROLLBACK_PLAN.CREATED_AT),
                    record.get(ROLLBACK_PLAN.UPDATED_AT)));
  }

  private List<TimelineSnapshot> loadTimeline(String tenantId, String incidentId) {
    return dsl.select(
            INCIDENT_TIMELINE.ID,
            INCIDENT_TIMELINE.EVENT_TYPE,
            INCIDENT_TIMELINE.TITLE,
            INCIDENT_TIMELINE.DESCRIPTION,
            INCIDENT_TIMELINE.EVENT_TIME)
        .from(INCIDENT_TIMELINE)
        .join(INCIDENT)
        .on(INCIDENT.ID.eq(INCIDENT_TIMELINE.INCIDENT_ID))
        .and(INCIDENT.TENANT_ID.eq(tenantId))
        .where(INCIDENT_TIMELINE.INCIDENT_ID.eq(incidentId))
        .orderBy(INCIDENT_TIMELINE.EVENT_TIME.asc())
        .fetch(
            record ->
                new TimelineSnapshot(
                    record.get(INCIDENT_TIMELINE.ID),
                    record.get(INCIDENT_TIMELINE.EVENT_TYPE),
                    record.get(INCIDENT_TIMELINE.TITLE),
                    record.get(INCIDENT_TIMELINE.DESCRIPTION),
                    record.get(INCIDENT_TIMELINE.EVENT_TIME)));
  }
}

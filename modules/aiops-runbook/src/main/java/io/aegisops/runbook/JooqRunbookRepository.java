package io.aegisops.runbook;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.AI_DIAGNOSIS;
import static io.aegisops.persistence.jooq.Tables.ALERT_EVENT;
import static io.aegisops.persistence.jooq.Tables.AUTOMATION_PLAN;
import static io.aegisops.persistence.jooq.Tables.AUTOMATION_PLAN_STEP;
import static io.aegisops.persistence.jooq.Tables.INCIDENT;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_EVENT;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_TIMELINE;
import static io.aegisops.persistence.jooq.Tables.RCA_ANALYSIS;
import static io.aegisops.persistence.jooq.Tables.RUNBOOK;
import static io.aegisops.persistence.jooq.Tables.RUNBOOK_STEP_TEMPLATE;

import io.aegisops.runbook.dto.AiDiagnosisForPlanRecord;
import io.aegisops.runbook.dto.AlertForPlanRecord;
import io.aegisops.runbook.dto.AutomationPlanCreateCommand;
import io.aegisops.runbook.dto.AutomationPlanRecord;
import io.aegisops.runbook.dto.AutomationPlanStepCreateCommand;
import io.aegisops.runbook.dto.AutomationPlanStepRecord;
import io.aegisops.runbook.dto.IncidentForPlanRecord;
import io.aegisops.runbook.dto.RcaForPlanRecord;
import io.aegisops.runbook.dto.RunbookCreateCommand;
import io.aegisops.runbook.dto.RunbookRecord;
import io.aegisops.runbook.dto.RunbookStepTemplateCreateCommand;
import io.aegisops.runbook.dto.RunbookStepTemplateRecord;
import io.aegisops.runbook.dto.TimelineCreateCommand;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** jOOQ generated Tables based repository for runbook and automation plan. */
@Repository
public class JooqRunbookRepository implements RunbookRepository {
  private final DSLContext dsl;

  public JooqRunbookRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public Optional<IncidentForPlanRecord> findIncident(String tenantId, String incidentId) {
    return dsl.select(
            INCIDENT.ID,
            INCIDENT.TENANT_ID,
            INCIDENT.TITLE,
            INCIDENT.SUMMARY,
            INCIDENT.SEVERITY,
            INCIDENT.STATUS,
            INCIDENT.PRIMARY_ASSET_ID,
            INCIDENT.SUSPECTED_ROOT_CAUSE,
            INCIDENT.LAST_SEEN_AT,
            INCIDENT.UPDATED_AT,
            INCIDENT.CREATED_AT)
        .from(INCIDENT)
        .where(INCIDENT.TENANT_ID.eq(tenantId))
        .and(INCIDENT.ID.eq(incidentId))
        .fetchOptional(JooqRunbookRepository::toIncidentForPlan);
  }

  @Override
  public List<AlertForPlanRecord> listIncidentAlerts(String tenantId, String incidentId) {
    return dsl.select(
            ALERT_EVENT.ID,
            ALERT_EVENT.SEVERITY,
            ALERT_EVENT.TITLE,
            ALERT_EVENT.DESCRIPTION,
            ALERT_EVENT.ASSET_ID,
            ALERT_EVENT.ENTITY_NAME,
            ALERT_EVENT.FINGERPRINT,
            ALERT_EVENT.LABELS.cast(String.class).as("labels_json"))
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
        .fetch(JooqRunbookRepository::toAlertForPlan);
  }

  @Override
  public Optional<AiDiagnosisForPlanRecord> findLatestDiagnosis(
      String tenantId, String incidentId) {
    return dsl.select(
            AI_DIAGNOSIS.ID,
            AI_DIAGNOSIS.SUMMARY,
            AI_DIAGNOSIS.ROOT_CAUSE,
            AI_DIAGNOSIS.IMPACT,
            AI_DIAGNOSIS.NEXT_STEPS.cast(String.class).as("next_steps_json"),
            AI_DIAGNOSIS.RUNBOOK_SUGGESTIONS.cast(String.class).as("runbook_suggestions_json"),
            AI_DIAGNOSIS.RISKS.cast(String.class).as("risks_json"),
            AI_DIAGNOSIS.CREATED_AT)
        .from(AI_DIAGNOSIS)
        .where(AI_DIAGNOSIS.TENANT_ID.eq(tenantId))
        .and(AI_DIAGNOSIS.INCIDENT_ID.eq(incidentId))
        .orderBy(AI_DIAGNOSIS.CREATED_AT.desc())
        .limit(1)
        .fetchOptional(JooqRunbookRepository::toAiDiagnosisForPlan);
  }

  @Override
  public Optional<RcaForPlanRecord> findLatestRca(String tenantId, String incidentId) {
    return dsl.select(
            RCA_ANALYSIS.ID,
            RCA_ANALYSIS.SUSPECTED_ROOT_CAUSE,
            RCA_ANALYSIS.CONFIDENCE,
            RCA_ANALYSIS.SUMMARY,
            RCA_ANALYSIS.EVIDENCE.cast(String.class).as("evidence_json"),
            RCA_ANALYSIS.CREATED_AT)
        .from(RCA_ANALYSIS)
        .where(RCA_ANALYSIS.TENANT_ID.eq(tenantId))
        .and(RCA_ANALYSIS.INCIDENT_ID.eq(incidentId))
        .orderBy(RCA_ANALYSIS.CREATED_AT.desc())
        .limit(1)
        .fetchOptional(JooqRunbookRepository::toRcaForPlan);
  }

  @Override
  public List<RunbookRecord> listRunbooks(String tenantId, boolean includeDisabled) {
    Condition condition = RUNBOOK.TENANT_ID.eq(tenantId).or(RUNBOOK.TENANT_ID.isNull());

    if (!includeDisabled) {
      condition = condition.and(RUNBOOK.ENABLED.isTrue());
    }

    return dsl.select(
            RUNBOOK.ID,
            RUNBOOK.TENANT_ID,
            RUNBOOK.NAME,
            RUNBOOK.DESCRIPTION,
            RUNBOOK.CATEGORY,
            RUNBOOK.RISK_LEVEL,
            RUNBOOK.ENABLED,
            RUNBOOK.MATCHERS.cast(String.class).as("matchers_json"),
            RUNBOOK.VARIABLES.cast(String.class).as("variables_json"),
            RUNBOOK.CREATED_AT,
            RUNBOOK.UPDATED_AT)
        .from(RUNBOOK)
        .where(condition)
        .orderBy(RUNBOOK.TENANT_ID.asc().nullsFirst(), RUNBOOK.NAME.asc())
        .fetch(JooqRunbookRepository::toRunbook);
  }

  @Override
  public Optional<RunbookRecord> findRunbook(String tenantId, String runbookId) {
    return dsl.select(
            RUNBOOK.ID,
            RUNBOOK.TENANT_ID,
            RUNBOOK.NAME,
            RUNBOOK.DESCRIPTION,
            RUNBOOK.CATEGORY,
            RUNBOOK.RISK_LEVEL,
            RUNBOOK.ENABLED,
            RUNBOOK.MATCHERS.cast(String.class).as("matchers_json"),
            RUNBOOK.VARIABLES.cast(String.class).as("variables_json"),
            RUNBOOK.CREATED_AT,
            RUNBOOK.UPDATED_AT)
        .from(RUNBOOK)
        .where(RUNBOOK.ID.eq(runbookId))
        .and(RUNBOOK.TENANT_ID.eq(tenantId).or(RUNBOOK.TENANT_ID.isNull()))
        .fetchOptional(JooqRunbookRepository::toRunbook);
  }

  @Override
  public List<RunbookStepTemplateRecord> listRunbookSteps(String runbookId) {
    return dsl.select(
            RUNBOOK_STEP_TEMPLATE.ID,
            RUNBOOK_STEP_TEMPLATE.RUNBOOK_ID,
            RUNBOOK_STEP_TEMPLATE.SEQUENCE_NO,
            RUNBOOK_STEP_TEMPLATE.NAME,
            RUNBOOK_STEP_TEMPLATE.ACTION_TYPE,
            RUNBOOK_STEP_TEMPLATE.TARGET_TYPE,
            RUNBOOK_STEP_TEMPLATE.COMMAND_TEMPLATE,
            RUNBOOK_STEP_TEMPLATE.DESCRIPTION,
            RUNBOOK_STEP_TEMPLATE.EXPECTED_RESULT,
            RUNBOOK_STEP_TEMPLATE.ROLLBACK_HINT,
            RUNBOOK_STEP_TEMPLATE.REQUIRES_APPROVAL,
            RUNBOOK_STEP_TEMPLATE.TIMEOUT_SECONDS,
            RUNBOOK_STEP_TEMPLATE.METADATA.cast(String.class).as("metadata_json"))
        .from(RUNBOOK_STEP_TEMPLATE)
        .where(RUNBOOK_STEP_TEMPLATE.RUNBOOK_ID.eq(runbookId))
        .orderBy(RUNBOOK_STEP_TEMPLATE.SEQUENCE_NO.asc())
        .fetch(JooqRunbookRepository::toStepTemplate);
  }

  @Override
  public void createRunbook(RunbookCreateCommand command) {
    dsl.insertInto(RUNBOOK)
        .set(RUNBOOK.ID, command.id())
        .set(RUNBOOK.TENANT_ID, command.tenantId())
        .set(RUNBOOK.NAME, command.name())
        .set(RUNBOOK.DESCRIPTION, command.description())
        .set(RUNBOOK.CATEGORY, command.category())
        .set(RUNBOOK.RISK_LEVEL, command.riskLevel())
        .set(RUNBOOK.ENABLED, command.enabled())
        .set(RUNBOOK.MATCHERS, jsonbValue(command.matchersJson()))
        .set(RUNBOOK.VARIABLES, jsonbValue(command.variablesJson()))
        .set(RUNBOOK.CREATED_AT, DSL.currentOffsetDateTime())
        .set(RUNBOOK.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createRunbookSteps(List<RunbookStepTemplateCreateCommand> commands) {
    for (RunbookStepTemplateCreateCommand command : commands) {
      dsl.insertInto(RUNBOOK_STEP_TEMPLATE)
          .set(RUNBOOK_STEP_TEMPLATE.ID, command.id())
          .set(RUNBOOK_STEP_TEMPLATE.RUNBOOK_ID, command.runbookId())
          .set(RUNBOOK_STEP_TEMPLATE.SEQUENCE_NO, command.sequenceNo())
          .set(RUNBOOK_STEP_TEMPLATE.NAME, command.name())
          .set(RUNBOOK_STEP_TEMPLATE.ACTION_TYPE, command.actionType())
          .set(RUNBOOK_STEP_TEMPLATE.TARGET_TYPE, command.targetType())
          .set(RUNBOOK_STEP_TEMPLATE.COMMAND_TEMPLATE, command.commandTemplate())
          .set(RUNBOOK_STEP_TEMPLATE.DESCRIPTION, command.description())
          .set(RUNBOOK_STEP_TEMPLATE.EXPECTED_RESULT, command.expectedResult())
          .set(RUNBOOK_STEP_TEMPLATE.ROLLBACK_HINT, command.rollbackHint())
          .set(RUNBOOK_STEP_TEMPLATE.REQUIRES_APPROVAL, command.requiresApproval())
          .set(RUNBOOK_STEP_TEMPLATE.TIMEOUT_SECONDS, command.timeoutSeconds())
          .set(RUNBOOK_STEP_TEMPLATE.METADATA, jsonbValue(command.metadataJson()))
          .set(RUNBOOK_STEP_TEMPLATE.CREATED_AT, DSL.currentOffsetDateTime())
          .execute();
    }
  }

  @Override
  public void setRunbookEnabled(String tenantId, String runbookId, boolean enabled) {
    dsl.update(RUNBOOK)
        .set(RUNBOOK.ENABLED, enabled)
        .set(RUNBOOK.UPDATED_AT, DSL.currentOffsetDateTime())
        .where(RUNBOOK.ID.eq(runbookId))
        .and(RUNBOOK.TENANT_ID.eq(tenantId))
        .execute();
  }

  @Override
  public Optional<AutomationPlanRecord> findLatestPlan(String tenantId, String incidentId) {
    return dsl.select(
            AUTOMATION_PLAN.ID,
            AUTOMATION_PLAN.TENANT_ID,
            AUTOMATION_PLAN.INCIDENT_ID,
            AUTOMATION_PLAN.RUNBOOK_ID,
            AUTOMATION_PLAN.AI_DIAGNOSIS_ID,
            AUTOMATION_PLAN.RCA_ANALYSIS_ID,
            AUTOMATION_PLAN.SOURCE,
            AUTOMATION_PLAN.STATUS,
            AUTOMATION_PLAN.RISK_LEVEL,
            AUTOMATION_PLAN.CONFIDENCE,
            AUTOMATION_PLAN.TITLE,
            AUTOMATION_PLAN.SUMMARY,
            AUTOMATION_PLAN.EVIDENCE.cast(String.class).as("evidence_json"),
            AUTOMATION_PLAN.CREATED_BY,
            AUTOMATION_PLAN.CREATED_AT,
            AUTOMATION_PLAN.UPDATED_AT)
        .from(AUTOMATION_PLAN)
        .where(AUTOMATION_PLAN.TENANT_ID.eq(tenantId))
        .and(AUTOMATION_PLAN.INCIDENT_ID.eq(incidentId))
        .orderBy(AUTOMATION_PLAN.CREATED_AT.desc())
        .limit(1)
        .fetchOptional(JooqRunbookRepository::toPlanRecord);
  }

  @Override
  public Optional<AutomationPlanRecord> findPlan(String tenantId, String planId) {
    return dsl.select(
            AUTOMATION_PLAN.ID,
            AUTOMATION_PLAN.TENANT_ID,
            AUTOMATION_PLAN.INCIDENT_ID,
            AUTOMATION_PLAN.RUNBOOK_ID,
            AUTOMATION_PLAN.AI_DIAGNOSIS_ID,
            AUTOMATION_PLAN.RCA_ANALYSIS_ID,
            AUTOMATION_PLAN.SOURCE,
            AUTOMATION_PLAN.STATUS,
            AUTOMATION_PLAN.RISK_LEVEL,
            AUTOMATION_PLAN.CONFIDENCE,
            AUTOMATION_PLAN.TITLE,
            AUTOMATION_PLAN.SUMMARY,
            AUTOMATION_PLAN.EVIDENCE.cast(String.class).as("evidence_json"),
            AUTOMATION_PLAN.CREATED_BY,
            AUTOMATION_PLAN.CREATED_AT,
            AUTOMATION_PLAN.UPDATED_AT)
        .from(AUTOMATION_PLAN)
        .where(AUTOMATION_PLAN.TENANT_ID.eq(tenantId))
        .and(AUTOMATION_PLAN.ID.eq(planId))
        .fetchOptional(JooqRunbookRepository::toPlanRecord);
  }

  @Override
  public List<AutomationPlanStepRecord> listPlanSteps(String planId) {
    return dsl.select(
            AUTOMATION_PLAN_STEP.ID,
            AUTOMATION_PLAN_STEP.PLAN_ID,
            AUTOMATION_PLAN_STEP.SEQUENCE_NO,
            AUTOMATION_PLAN_STEP.NAME,
            AUTOMATION_PLAN_STEP.ACTION_TYPE,
            AUTOMATION_PLAN_STEP.TARGET_TYPE,
            AUTOMATION_PLAN_STEP.ACTION_PAYLOAD.cast(String.class).as("action_payload_json"),
            AUTOMATION_PLAN_STEP.DESCRIPTION,
            AUTOMATION_PLAN_STEP.EXPECTED_RESULT,
            AUTOMATION_PLAN_STEP.ROLLBACK_HINT,
            AUTOMATION_PLAN_STEP.REQUIRES_APPROVAL,
            AUTOMATION_PLAN_STEP.STATUS,
            AUTOMATION_PLAN_STEP.CREATED_AT)
        .from(AUTOMATION_PLAN_STEP)
        .where(AUTOMATION_PLAN_STEP.PLAN_ID.eq(planId))
        .orderBy(AUTOMATION_PLAN_STEP.SEQUENCE_NO.asc())
        .fetch(JooqRunbookRepository::toPlanStepRecord);
  }

  @Override
  public void createPlan(AutomationPlanCreateCommand command) {
    dsl.insertInto(AUTOMATION_PLAN)
        .set(AUTOMATION_PLAN.ID, command.id())
        .set(AUTOMATION_PLAN.TENANT_ID, command.tenantId())
        .set(AUTOMATION_PLAN.INCIDENT_ID, command.incidentId())
        .set(AUTOMATION_PLAN.RUNBOOK_ID, command.runbookId())
        .set(AUTOMATION_PLAN.AI_DIAGNOSIS_ID, command.aiDiagnosisId())
        .set(AUTOMATION_PLAN.RCA_ANALYSIS_ID, command.rcaAnalysisId())
        .set(AUTOMATION_PLAN.SOURCE, command.source())
        .set(AUTOMATION_PLAN.STATUS, command.status())
        .set(AUTOMATION_PLAN.RISK_LEVEL, command.riskLevel())
        .set(AUTOMATION_PLAN.CONFIDENCE, command.confidence())
        .set(AUTOMATION_PLAN.TITLE, command.title())
        .set(AUTOMATION_PLAN.SUMMARY, command.summary())
        .set(AUTOMATION_PLAN.EVIDENCE, jsonbValue(command.evidenceJson()))
        .set(AUTOMATION_PLAN.CREATED_BY, command.createdBy())
        .set(AUTOMATION_PLAN.CREATED_AT, DSL.currentOffsetDateTime())
        .set(AUTOMATION_PLAN.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createPlanSteps(List<AutomationPlanStepCreateCommand> commands) {
    for (AutomationPlanStepCreateCommand command : commands) {
      dsl.insertInto(AUTOMATION_PLAN_STEP)
          .set(AUTOMATION_PLAN_STEP.ID, command.id())
          .set(AUTOMATION_PLAN_STEP.PLAN_ID, command.planId())
          .set(AUTOMATION_PLAN_STEP.SEQUENCE_NO, command.sequenceNo())
          .set(AUTOMATION_PLAN_STEP.NAME, command.name())
          .set(AUTOMATION_PLAN_STEP.ACTION_TYPE, command.actionType())
          .set(AUTOMATION_PLAN_STEP.TARGET_TYPE, command.targetType())
          .set(AUTOMATION_PLAN_STEP.ACTION_PAYLOAD, jsonbValue(command.actionPayloadJson()))
          .set(AUTOMATION_PLAN_STEP.DESCRIPTION, command.description())
          .set(AUTOMATION_PLAN_STEP.EXPECTED_RESULT, command.expectedResult())
          .set(AUTOMATION_PLAN_STEP.ROLLBACK_HINT, command.rollbackHint())
          .set(AUTOMATION_PLAN_STEP.REQUIRES_APPROVAL, command.requiresApproval())
          .set(AUTOMATION_PLAN_STEP.STATUS, command.status())
          .set(AUTOMATION_PLAN_STEP.CREATED_AT, DSL.currentOffsetDateTime())
          .execute();
    }
  }

  @Override
  public void addTimeline(TimelineCreateCommand command) {
    dsl.insertInto(INCIDENT_TIMELINE)
        .set(INCIDENT_TIMELINE.ID, command.id())
        .set(INCIDENT_TIMELINE.INCIDENT_ID, command.incidentId())
        .set(INCIDENT_TIMELINE.EVENT_TIME, command.eventTime())
        .set(INCIDENT_TIMELINE.EVENT_TYPE, command.eventType())
        .set(INCIDENT_TIMELINE.TITLE, command.title())
        .set(INCIDENT_TIMELINE.DESCRIPTION, command.description())
        .set(INCIDENT_TIMELINE.SOURCE, command.source())
        .set(INCIDENT_TIMELINE.PAYLOAD, jsonbValue(command.payloadJson()))
        .execute();
  }

  private static IncidentForPlanRecord toIncidentForPlan(org.jooq.Record record) {
    return new IncidentForPlanRecord(
        record.get(INCIDENT.ID),
        record.get(INCIDENT.TENANT_ID),
        record.get(INCIDENT.TITLE),
        record.get(INCIDENT.SUMMARY),
        record.get(INCIDENT.SEVERITY),
        record.get(INCIDENT.STATUS),
        record.get(INCIDENT.PRIMARY_ASSET_ID),
        record.get(INCIDENT.SUSPECTED_ROOT_CAUSE),
        record.get(INCIDENT.LAST_SEEN_AT),
        record.get(INCIDENT.UPDATED_AT),
        record.get(INCIDENT.CREATED_AT));
  }

  private static AlertForPlanRecord toAlertForPlan(org.jooq.Record record) {
    return new AlertForPlanRecord(
        record.get(ALERT_EVENT.ID),
        record.get(ALERT_EVENT.SEVERITY),
        record.get(ALERT_EVENT.TITLE),
        record.get(ALERT_EVENT.DESCRIPTION),
        record.get(ALERT_EVENT.ASSET_ID),
        record.get(ALERT_EVENT.ENTITY_NAME),
        record.get(ALERT_EVENT.FINGERPRINT),
        record.get("labels_json", String.class));
  }

  private static AiDiagnosisForPlanRecord toAiDiagnosisForPlan(org.jooq.Record record) {
    return new AiDiagnosisForPlanRecord(
        record.get(AI_DIAGNOSIS.ID),
        record.get(AI_DIAGNOSIS.SUMMARY),
        record.get(AI_DIAGNOSIS.ROOT_CAUSE),
        record.get(AI_DIAGNOSIS.IMPACT),
        record.get("next_steps_json", String.class),
        record.get("runbook_suggestions_json", String.class),
        record.get("risks_json", String.class),
        record.get(AI_DIAGNOSIS.CREATED_AT));
  }

  private static RcaForPlanRecord toRcaForPlan(org.jooq.Record record) {
    return new RcaForPlanRecord(
        record.get(RCA_ANALYSIS.ID),
        record.get(RCA_ANALYSIS.SUSPECTED_ROOT_CAUSE),
        record.get(RCA_ANALYSIS.CONFIDENCE),
        record.get(RCA_ANALYSIS.SUMMARY),
        record.get("evidence_json", String.class),
        record.get(RCA_ANALYSIS.CREATED_AT));
  }

  private static RunbookRecord toRunbook(org.jooq.Record record) {
    return new RunbookRecord(
        record.get(RUNBOOK.ID),
        record.get(RUNBOOK.TENANT_ID),
        record.get(RUNBOOK.NAME),
        record.get(RUNBOOK.DESCRIPTION),
        record.get(RUNBOOK.CATEGORY),
        record.get(RUNBOOK.RISK_LEVEL),
        Boolean.TRUE.equals(record.get(RUNBOOK.ENABLED)),
        record.get("matchers_json", String.class),
        record.get("variables_json", String.class),
        record.get(RUNBOOK.CREATED_AT),
        record.get(RUNBOOK.UPDATED_AT));
  }

  private static RunbookStepTemplateRecord toStepTemplate(org.jooq.Record record) {
    return new RunbookStepTemplateRecord(
        record.get(RUNBOOK_STEP_TEMPLATE.ID),
        record.get(RUNBOOK_STEP_TEMPLATE.RUNBOOK_ID),
        value(record.get(RUNBOOK_STEP_TEMPLATE.SEQUENCE_NO)),
        record.get(RUNBOOK_STEP_TEMPLATE.NAME),
        record.get(RUNBOOK_STEP_TEMPLATE.ACTION_TYPE),
        record.get(RUNBOOK_STEP_TEMPLATE.TARGET_TYPE),
        record.get(RUNBOOK_STEP_TEMPLATE.COMMAND_TEMPLATE),
        record.get(RUNBOOK_STEP_TEMPLATE.DESCRIPTION),
        record.get(RUNBOOK_STEP_TEMPLATE.EXPECTED_RESULT),
        record.get(RUNBOOK_STEP_TEMPLATE.ROLLBACK_HINT),
        Boolean.TRUE.equals(record.get(RUNBOOK_STEP_TEMPLATE.REQUIRES_APPROVAL)),
        value(record.get(RUNBOOK_STEP_TEMPLATE.TIMEOUT_SECONDS)),
        record.get("metadata_json", String.class));
  }

  private static AutomationPlanRecord toPlanRecord(org.jooq.Record record) {
    return new AutomationPlanRecord(
        record.get(AUTOMATION_PLAN.ID),
        record.get(AUTOMATION_PLAN.TENANT_ID),
        record.get(AUTOMATION_PLAN.INCIDENT_ID),
        record.get(AUTOMATION_PLAN.RUNBOOK_ID),
        record.get(AUTOMATION_PLAN.AI_DIAGNOSIS_ID),
        record.get(AUTOMATION_PLAN.RCA_ANALYSIS_ID),
        record.get(AUTOMATION_PLAN.SOURCE),
        record.get(AUTOMATION_PLAN.STATUS),
        record.get(AUTOMATION_PLAN.RISK_LEVEL),
        record.get(AUTOMATION_PLAN.CONFIDENCE),
        record.get(AUTOMATION_PLAN.TITLE),
        record.get(AUTOMATION_PLAN.SUMMARY),
        record.get("evidence_json", String.class),
        record.get(AUTOMATION_PLAN.CREATED_BY),
        record.get(AUTOMATION_PLAN.CREATED_AT),
        record.get(AUTOMATION_PLAN.UPDATED_AT));
  }

  private static AutomationPlanStepRecord toPlanStepRecord(org.jooq.Record record) {
    return new AutomationPlanStepRecord(
        record.get(AUTOMATION_PLAN_STEP.ID),
        record.get(AUTOMATION_PLAN_STEP.PLAN_ID),
        value(record.get(AUTOMATION_PLAN_STEP.SEQUENCE_NO)),
        record.get(AUTOMATION_PLAN_STEP.NAME),
        record.get(AUTOMATION_PLAN_STEP.ACTION_TYPE),
        record.get(AUTOMATION_PLAN_STEP.TARGET_TYPE),
        record.get("action_payload_json", String.class),
        record.get(AUTOMATION_PLAN_STEP.DESCRIPTION),
        record.get(AUTOMATION_PLAN_STEP.EXPECTED_RESULT),
        record.get(AUTOMATION_PLAN_STEP.ROLLBACK_HINT),
        Boolean.TRUE.equals(record.get(AUTOMATION_PLAN_STEP.REQUIRES_APPROVAL)),
        record.get(AUTOMATION_PLAN_STEP.STATUS),
        record.get(AUTOMATION_PLAN_STEP.CREATED_AT));
  }

  private static int value(Integer value) {
    return value == null ? 0 : value;
  }
}

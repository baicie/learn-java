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
import io.aegisops.runbook.dto.ApprovalDecisionCommand;
import io.aegisops.runbook.dto.ApprovalDecisionRecord;
import io.aegisops.runbook.dto.ApprovalPolicyRecord;
import io.aegisops.runbook.dto.ApprovalProgressUpdateCommand;
import io.aegisops.runbook.dto.AutomationApprovalCreateCommand;
import io.aegisops.runbook.dto.AutomationApprovalRecord;
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

@Repository
public class JooqRunbookRepository implements RunbookRepository {
  private final DSLContext dsl;
  private final ApprovalPersistenceSupport approvalSupport;

  public JooqRunbookRepository(DSLContext dsl) {
    this.dsl = dsl;
    this.approvalSupport = new ApprovalPersistenceSupport(dsl);
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
        .fetchOptional(RecordMappers::toIncidentForPlan);
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
        .fetch(RecordMappers::toAlertForPlan);
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
        .fetchOptional(RecordMappers::toAiDiagnosisForPlan);
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
        .fetchOptional(RecordMappers::toRcaForPlan);
  }

  @Override
  public List<RunbookRecord> listRunbooks(String tenantId, boolean includeDisabled) {
    Condition c = RUNBOOK.TENANT_ID.eq(tenantId).or(RUNBOOK.TENANT_ID.isNull());
    if (!includeDisabled) {
      c = c.and(RUNBOOK.ENABLED.isTrue());
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
        .where(c)
        .orderBy(RUNBOOK.TENANT_ID.asc().nullsFirst(), RUNBOOK.NAME.asc())
        .fetch(RecordMappers::toRunbook);
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
        .fetchOptional(RecordMappers::toRunbook);
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
        .fetch(RecordMappers::toStepTemplate);
  }

  @Override
  public void createRunbook(RunbookCreateCommand cmd) {
    dsl.insertInto(RUNBOOK)
        .set(RUNBOOK.ID, cmd.id())
        .set(RUNBOOK.TENANT_ID, cmd.tenantId())
        .set(RUNBOOK.NAME, cmd.name())
        .set(RUNBOOK.DESCRIPTION, cmd.description())
        .set(RUNBOOK.CATEGORY, cmd.category())
        .set(RUNBOOK.RISK_LEVEL, cmd.riskLevel())
        .set(RUNBOOK.ENABLED, cmd.enabled())
        .set(RUNBOOK.MATCHERS, jsonbValue(cmd.matchersJson()))
        .set(RUNBOOK.VARIABLES, jsonbValue(cmd.variablesJson()))
        .set(RUNBOOK.CREATED_AT, DSL.currentOffsetDateTime())
        .set(RUNBOOK.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createRunbookSteps(List<RunbookStepTemplateCreateCommand> cmds) {
    for (RunbookStepTemplateCreateCommand cmd : cmds) {
      dsl.insertInto(RUNBOOK_STEP_TEMPLATE)
          .set(RUNBOOK_STEP_TEMPLATE.ID, cmd.id())
          .set(RUNBOOK_STEP_TEMPLATE.RUNBOOK_ID, cmd.runbookId())
          .set(RUNBOOK_STEP_TEMPLATE.SEQUENCE_NO, cmd.sequenceNo())
          .set(RUNBOOK_STEP_TEMPLATE.NAME, cmd.name())
          .set(RUNBOOK_STEP_TEMPLATE.ACTION_TYPE, cmd.actionType())
          .set(RUNBOOK_STEP_TEMPLATE.TARGET_TYPE, cmd.targetType())
          .set(RUNBOOK_STEP_TEMPLATE.COMMAND_TEMPLATE, cmd.commandTemplate())
          .set(RUNBOOK_STEP_TEMPLATE.DESCRIPTION, cmd.description())
          .set(RUNBOOK_STEP_TEMPLATE.EXPECTED_RESULT, cmd.expectedResult())
          .set(RUNBOOK_STEP_TEMPLATE.ROLLBACK_HINT, cmd.rollbackHint())
          .set(RUNBOOK_STEP_TEMPLATE.REQUIRES_APPROVAL, cmd.requiresApproval())
          .set(RUNBOOK_STEP_TEMPLATE.TIMEOUT_SECONDS, cmd.timeoutSeconds())
          .set(RUNBOOK_STEP_TEMPLATE.METADATA, jsonbValue(cmd.metadataJson()))
          .set(RUNBOOK_STEP_TEMPLATE.CREATED_AT, DSL.currentOffsetDateTime())
          .execute();
    }
  }

  @Override
  public boolean setRunbookEnabled(String tenantId, String runbookId, boolean enabled) {
    int updated =
        dsl.update(RUNBOOK)
            .set(RUNBOOK.ENABLED, enabled)
            .set(RUNBOOK.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(RUNBOOK.ID.eq(runbookId))
            .and(RUNBOOK.TENANT_ID.eq(tenantId))
            .execute();
    return updated > 0;
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
        .fetchOptional(RecordMappers::toPlanRecord);
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
        .fetchOptional(RecordMappers::toPlanRecord);
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
        .fetch(RecordMappers::toPlanStepRecord);
  }

  @Override
  public void createPlan(AutomationPlanCreateCommand cmd) {
    dsl.insertInto(AUTOMATION_PLAN)
        .set(AUTOMATION_PLAN.ID, cmd.id())
        .set(AUTOMATION_PLAN.TENANT_ID, cmd.tenantId())
        .set(AUTOMATION_PLAN.INCIDENT_ID, cmd.incidentId())
        .set(AUTOMATION_PLAN.RUNBOOK_ID, cmd.runbookId())
        .set(AUTOMATION_PLAN.AI_DIAGNOSIS_ID, cmd.aiDiagnosisId())
        .set(AUTOMATION_PLAN.RCA_ANALYSIS_ID, cmd.rcaAnalysisId())
        .set(AUTOMATION_PLAN.SOURCE, cmd.source())
        .set(AUTOMATION_PLAN.STATUS, cmd.status())
        .set(AUTOMATION_PLAN.RISK_LEVEL, cmd.riskLevel())
        .set(AUTOMATION_PLAN.CONFIDENCE, cmd.confidence())
        .set(AUTOMATION_PLAN.TITLE, cmd.title())
        .set(AUTOMATION_PLAN.SUMMARY, cmd.summary())
        .set(AUTOMATION_PLAN.EVIDENCE, jsonbValue(cmd.evidenceJson()))
        .set(AUTOMATION_PLAN.CREATED_BY, cmd.createdBy())
        .set(AUTOMATION_PLAN.CREATED_AT, DSL.currentOffsetDateTime())
        .set(AUTOMATION_PLAN.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createPlanSteps(List<AutomationPlanStepCreateCommand> cmds) {
    for (AutomationPlanStepCreateCommand cmd : cmds) {
      dsl.insertInto(AUTOMATION_PLAN_STEP)
          .set(AUTOMATION_PLAN_STEP.ID, cmd.id())
          .set(AUTOMATION_PLAN_STEP.PLAN_ID, cmd.planId())
          .set(AUTOMATION_PLAN_STEP.SEQUENCE_NO, cmd.sequenceNo())
          .set(AUTOMATION_PLAN_STEP.NAME, cmd.name())
          .set(AUTOMATION_PLAN_STEP.ACTION_TYPE, cmd.actionType())
          .set(AUTOMATION_PLAN_STEP.TARGET_TYPE, cmd.targetType())
          .set(AUTOMATION_PLAN_STEP.ACTION_PAYLOAD, jsonbValue(cmd.actionPayloadJson()))
          .set(AUTOMATION_PLAN_STEP.DESCRIPTION, cmd.description())
          .set(AUTOMATION_PLAN_STEP.EXPECTED_RESULT, cmd.expectedResult())
          .set(AUTOMATION_PLAN_STEP.ROLLBACK_HINT, cmd.rollbackHint())
          .set(AUTOMATION_PLAN_STEP.REQUIRES_APPROVAL, cmd.requiresApproval())
          .set(AUTOMATION_PLAN_STEP.STATUS, cmd.status())
          .set(AUTOMATION_PLAN_STEP.CREATED_AT, DSL.currentOffsetDateTime())
          .execute();
    }
  }

  @Override
  public boolean updatePlanStatus(String tenantId, String planId, String status) {
    int updated =
        dsl.update(AUTOMATION_PLAN)
            .set(AUTOMATION_PLAN.STATUS, status)
            .set(AUTOMATION_PLAN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(AUTOMATION_PLAN.TENANT_ID.eq(tenantId))
            .and(AUTOMATION_PLAN.ID.eq(planId))
            .execute();
    return updated > 0;
  }

  @Override
  public Optional<ApprovalPolicyRecord> findApprovalPolicy(String tenantId, String riskLevel) {
    return approvalSupport.findApprovalPolicy(tenantId, riskLevel);
  }

  @Override
  public Optional<ApprovalPolicyRecord> findGlobalApprovalPolicy(String riskLevel) {
    return approvalSupport.findGlobalApprovalPolicy(riskLevel);
  }

  @Override
  public void createApproval(AutomationApprovalCreateCommand cmd) {
    approvalSupport.createApproval(cmd);
  }

  @Override
  public Optional<AutomationApprovalRecord> findApproval(String tenantId, String approvalId) {
    return approvalSupport.findApproval(tenantId, approvalId);
  }

  @Override
  public Optional<AutomationApprovalRecord> findLatestApprovalByPlan(
      String tenantId, String planId) {
    return approvalSupport.findLatestApprovalByPlan(tenantId, planId);
  }

  @Override
  public boolean updateApprovalProgress(ApprovalProgressUpdateCommand cmd) {
    return approvalSupport.updateApprovalProgress(cmd);
  }

  @Override
  public boolean decisionExists(String tenantId, String approvalId, String reviewer) {
    return approvalSupport.decisionExists(tenantId, approvalId, reviewer);
  }

  @Override
  public void createDecision(ApprovalDecisionCommand cmd) {
    approvalSupport.createDecision(cmd);
  }

  @Override
  public List<ApprovalDecisionRecord> listDecisions(String approvalId) {
    return approvalSupport.listDecisions(approvalId);
  }

  @Override
  public void addTimeline(TimelineCreateCommand cmd) {
    dsl.insertInto(INCIDENT_TIMELINE)
        .set(INCIDENT_TIMELINE.ID, cmd.id())
        .set(INCIDENT_TIMELINE.INCIDENT_ID, cmd.incidentId())
        .set(INCIDENT_TIMELINE.EVENT_TIME, cmd.eventTime())
        .set(INCIDENT_TIMELINE.EVENT_TYPE, cmd.eventType())
        .set(INCIDENT_TIMELINE.TITLE, cmd.title())
        .set(INCIDENT_TIMELINE.DESCRIPTION, cmd.description())
        .set(INCIDENT_TIMELINE.SOURCE, cmd.source())
        .set(INCIDENT_TIMELINE.PAYLOAD, jsonbValue(cmd.payloadJson()))
        .execute();
  }
}

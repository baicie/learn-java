package io.aegisops.runbook;

import static io.aegisops.persistence.jooq.Tables.AI_DIAGNOSIS;
import static io.aegisops.persistence.jooq.Tables.ALERT_EVENT;
import static io.aegisops.persistence.jooq.Tables.AUTOMATION_PLAN;
import static io.aegisops.persistence.jooq.Tables.AUTOMATION_PLAN_STEP;
import static io.aegisops.persistence.jooq.Tables.INCIDENT;
import static io.aegisops.persistence.jooq.Tables.RCA_ANALYSIS;
import static io.aegisops.persistence.jooq.Tables.RUNBOOK;
import static io.aegisops.persistence.jooq.Tables.RUNBOOK_STEP_TEMPLATE;

import io.aegisops.runbook.dto.AiDiagnosisForPlanRecord;
import io.aegisops.runbook.dto.AlertForPlanRecord;
import io.aegisops.runbook.dto.AutomationPlanRecord;
import io.aegisops.runbook.dto.AutomationPlanStepRecord;
import io.aegisops.runbook.dto.IncidentForPlanRecord;
import io.aegisops.runbook.dto.RcaForPlanRecord;
import io.aegisops.runbook.dto.RunbookRecord;
import io.aegisops.runbook.dto.RunbookStepTemplateRecord;
import org.jooq.Record;

/**
 * jOOQ record-to-DTO mappers shared across repository implementations.
 */
final class RecordMappers {

  private RecordMappers() {}

  static IncidentForPlanRecord toIncidentForPlan(Record r) {
    return new IncidentForPlanRecord(
        r.get(INCIDENT.ID),
        r.get(INCIDENT.TENANT_ID),
        r.get(INCIDENT.TITLE),
        r.get(INCIDENT.SUMMARY),
        r.get(INCIDENT.SEVERITY),
        r.get(INCIDENT.STATUS),
        r.get(INCIDENT.PRIMARY_ASSET_ID),
        r.get(INCIDENT.SUSPECTED_ROOT_CAUSE),
        r.get(INCIDENT.LAST_SEEN_AT),
        r.get(INCIDENT.UPDATED_AT),
        r.get(INCIDENT.CREATED_AT));
  }

  static AlertForPlanRecord toAlertForPlan(Record r) {
    return new AlertForPlanRecord(
        r.get(ALERT_EVENT.ID),
        r.get(ALERT_EVENT.SEVERITY),
        r.get(ALERT_EVENT.TITLE),
        r.get(ALERT_EVENT.DESCRIPTION),
        r.get(ALERT_EVENT.ASSET_ID),
        r.get(ALERT_EVENT.ENTITY_NAME),
        r.get(ALERT_EVENT.FINGERPRINT),
        r.get("labels_json", String.class));
  }

  static AiDiagnosisForPlanRecord toAiDiagnosisForPlan(Record r) {
    return new AiDiagnosisForPlanRecord(
        r.get(AI_DIAGNOSIS.ID),
        r.get(AI_DIAGNOSIS.SUMMARY),
        r.get(AI_DIAGNOSIS.ROOT_CAUSE),
        r.get(AI_DIAGNOSIS.IMPACT),
        r.get("next_steps_json", String.class),
        r.get("runbook_suggestions_json", String.class),
        r.get("risks_json", String.class),
        r.get(AI_DIAGNOSIS.CREATED_AT));
  }

  static RcaForPlanRecord toRcaForPlan(Record r) {
    return new RcaForPlanRecord(
        r.get(RCA_ANALYSIS.ID),
        r.get(RCA_ANALYSIS.SUSPECTED_ROOT_CAUSE),
        r.get(RCA_ANALYSIS.CONFIDENCE),
        r.get(RCA_ANALYSIS.SUMMARY),
        r.get("evidence_json", String.class),
        r.get(RCA_ANALYSIS.CREATED_AT));
  }

  static RunbookRecord toRunbook(Record r) {
    return new RunbookRecord(
        r.get(RUNBOOK.ID),
        r.get(RUNBOOK.TENANT_ID),
        r.get(RUNBOOK.NAME),
        r.get(RUNBOOK.DESCRIPTION),
        r.get(RUNBOOK.CATEGORY),
        r.get(RUNBOOK.RISK_LEVEL),
        Boolean.TRUE.equals(r.get(RUNBOOK.ENABLED)),
        r.get("matchers_json", String.class),
        r.get("variables_json", String.class),
        r.get(RUNBOOK.CREATED_AT),
        r.get(RUNBOOK.UPDATED_AT));
  }

  static RunbookStepTemplateRecord toStepTemplate(Record r) {
    Integer seq = r.get(RUNBOOK_STEP_TEMPLATE.SEQUENCE_NO);
    Integer to = r.get(RUNBOOK_STEP_TEMPLATE.TIMEOUT_SECONDS);
    return new RunbookStepTemplateRecord(
        r.get(RUNBOOK_STEP_TEMPLATE.ID),
        r.get(RUNBOOK_STEP_TEMPLATE.RUNBOOK_ID),
        seq == null ? 0 : seq,
        r.get(RUNBOOK_STEP_TEMPLATE.NAME),
        r.get(RUNBOOK_STEP_TEMPLATE.ACTION_TYPE),
        r.get(RUNBOOK_STEP_TEMPLATE.TARGET_TYPE),
        r.get(RUNBOOK_STEP_TEMPLATE.COMMAND_TEMPLATE),
        r.get(RUNBOOK_STEP_TEMPLATE.DESCRIPTION),
        r.get(RUNBOOK_STEP_TEMPLATE.EXPECTED_RESULT),
        r.get(RUNBOOK_STEP_TEMPLATE.ROLLBACK_HINT),
        Boolean.TRUE.equals(r.get(RUNBOOK_STEP_TEMPLATE.REQUIRES_APPROVAL)),
        to == null ? 0 : to,
        r.get("metadata_json", String.class));
  }

  static AutomationPlanRecord toPlanRecord(Record r) {
    return new AutomationPlanRecord(
        r.get(AUTOMATION_PLAN.ID),
        r.get(AUTOMATION_PLAN.TENANT_ID),
        r.get(AUTOMATION_PLAN.INCIDENT_ID),
        r.get(AUTOMATION_PLAN.RUNBOOK_ID),
        r.get(AUTOMATION_PLAN.AI_DIAGNOSIS_ID),
        r.get(AUTOMATION_PLAN.RCA_ANALYSIS_ID),
        r.get(AUTOMATION_PLAN.SOURCE),
        r.get(AUTOMATION_PLAN.STATUS),
        r.get(AUTOMATION_PLAN.RISK_LEVEL),
        r.get(AUTOMATION_PLAN.CONFIDENCE),
        r.get(AUTOMATION_PLAN.TITLE),
        r.get(AUTOMATION_PLAN.SUMMARY),
        r.get("evidence_json", String.class),
        r.get(AUTOMATION_PLAN.CREATED_BY),
        r.get(AUTOMATION_PLAN.CREATED_AT),
        r.get(AUTOMATION_PLAN.UPDATED_AT));
  }

  static AutomationPlanStepRecord toPlanStepRecord(Record r) {
    Integer seq = r.get(AUTOMATION_PLAN_STEP.SEQUENCE_NO);
    return new AutomationPlanStepRecord(
        r.get(AUTOMATION_PLAN_STEP.ID),
        r.get(AUTOMATION_PLAN_STEP.PLAN_ID),
        seq == null ? 0 : seq,
        r.get(AUTOMATION_PLAN_STEP.NAME),
        r.get(AUTOMATION_PLAN_STEP.ACTION_TYPE),
        r.get(AUTOMATION_PLAN_STEP.TARGET_TYPE),
        r.get("action_payload_json", String.class),
        r.get(AUTOMATION_PLAN_STEP.DESCRIPTION),
        r.get(AUTOMATION_PLAN_STEP.EXPECTED_RESULT),
        r.get(AUTOMATION_PLAN_STEP.ROLLBACK_HINT),
        Boolean.TRUE.equals(r.get(AUTOMATION_PLAN_STEP.REQUIRES_APPROVAL)),
        r.get(AUTOMATION_PLAN_STEP.STATUS),
        r.get(AUTOMATION_PLAN_STEP.CREATED_AT));
  }
}
